package com.xbrother.lanproxy.server.handlers;

import java.util.ArrayList;
import java.util.List;

import com.xbrother.lanproxy.common.JsonUtil;
import com.xbrother.lanproxy.protocol.Constants;
import com.xbrother.lanproxy.protocol.ProxyMessage;
import com.xbrother.lanproxy.server.ProxyChannelManager;
import com.xbrother.lanproxy.server.config.ProxyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * 处理来自客户端的请求
 *
 * @author zhoubowen
 */
public class ServerChannelHandler extends SimpleChannelInboundHandler<ProxyMessage> {

    private static Logger logger = LoggerFactory.getLogger(ServerChannelHandler.class);

    /**
     * 接收到客户端的数据的时候执行
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProxyMessage proxyMessage) throws Exception {
        logger.debug("ProxyMessage received {} {} {}", proxyMessage.getType(), proxyMessage.getUri(), proxyMessage.getSerialNumber());
        switch (proxyMessage.getType()) {
            case ProxyMessage.TYPE_HEARTBEAT:
                handleHeartbeatMessage(ctx, proxyMessage);
                break;
            case ProxyMessage.C_TYPE_AUTH:
                handleAuthMessage(ctx, proxyMessage);
                break;
            case ProxyMessage.TYPE_CONNECT:
                handleConnectMessage(ctx, proxyMessage);
                break;
            case ProxyMessage.TYPE_DISCONNECT:
                handleDisconnectMessage(ctx, proxyMessage);
                break;
            case ProxyMessage.P_TYPE_TRANSFER:
                handleTransferMessage(ctx, proxyMessage);
                break;
            default:
                logger.error("协议不符合要求");
                break;
        }
    }

    // 处理客户端的数据包
    private void handleTransferMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        Channel userChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        if (userChannel != null) {
            ByteBuf buf = ctx.alloc().buffer(proxyMessage.getData().length);
            buf.writeBytes(proxyMessage.getData());
            userChannel.writeAndFlush(buf);
        }
    }

    // 处理客户端的断开请求
    private void handleDisconnectMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        String clientKey = ctx.channel().attr(Constants.CLIENT_KEY).get();
        // 代理连接没有连上服务器由控制连接发送用户端断开连接消息
        if (clientKey == null) {
            String userId = proxyMessage.getUri();
            Channel userChannel = ProxyChannelManager.removeUserChannelFromCmdChannel(ctx.channel(), userId);
            if (userChannel != null) {
                // 数据发送完成后再关闭连接，解决http1.0数据传输问题
                userChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
            return;
        }

        Channel cmdChannel = ProxyChannelManager.getCmdChannel(clientKey);
        if (cmdChannel == null) {
            logger.warn("ConnectMessage :error cmd channel key {}", ctx.channel().attr(Constants.CLIENT_KEY).get());
            return;
        }

        Channel userChannel = ProxyChannelManager.removeUserChannelFromCmdChannel(cmdChannel, ctx.channel().attr(Constants.USER_ID).get());
        if (userChannel != null) {
            // 数据发送完成后再关闭连接，解决http1.0数据传输问题
            userChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);

            ctx.channel().attr(Constants.NEXT_CHANNEL).set(null);
            ctx.channel().attr(Constants.CLIENT_KEY).set(null);
            ctx.channel().attr(Constants.USER_ID).set(null);
        }
    }

    // 处理客户端的连接请求
    private void handleConnectMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        String uri = proxyMessage.getUri();
        if (uri == null) {
            ctx.channel().close();
            logger.warn("ConnectMessage:null uri");
            return;
        }

        String[] tokens = uri.split("@");
        if (tokens.length != 2) {
            ctx.channel().close();
            logger.warn("ConnectMessage: error uri :", tokens.toString());
            return;
        }
        logger.info("客户端传来的 Token: {}", tokens.toString());

        Channel cmdChannel = ProxyChannelManager.getCmdChannel(tokens[1]);
        if (cmdChannel == null) {
            ctx.channel().close();
            logger.warn("ConnectMessage: error cmd channel key {}", tokens[1]);
            return;
        }

        Channel userChannel = ProxyChannelManager.getUserChannel(cmdChannel, tokens[0]);
        if (userChannel != null) {
            ctx.channel().attr(Constants.USER_ID).set(tokens[0]);
            ctx.channel().attr(Constants.CLIENT_KEY).set(tokens[1]);
            ctx.channel().attr(Constants.NEXT_CHANNEL).set(userChannel);
            userChannel.attr(Constants.NEXT_CHANNEL).set(ctx.channel());
            // 代理客户端与后端服务器连接成功，修改用户连接为可读状态
            userChannel.config().setOption(ChannelOption.AUTO_READ, true);
        }
    }

    // 处理客户端发来的心跳包
    private void handleHeartbeatMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        ProxyMessage heartbeatMessage = new ProxyMessage();
        heartbeatMessage.setSerialNumber(heartbeatMessage.getSerialNumber());
        heartbeatMessage.setType(ProxyMessage.TYPE_HEARTBEAT);
        logger.debug("response heartbeat message {}", ctx.channel());
        ctx.channel().writeAndFlush(heartbeatMessage);
    }

    // 处理客户端发来的认证包
    private void handleAuthMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        String clientKey = proxyMessage.getUri();
        List<Integer> ports = ProxyConfig.getInstance().getClientInetPorts(clientKey);
        List<ProxyConfig.Client> clients = ProxyConfig.getInstance().getClients();

        boolean registeredFlag = false;
        for (int i = 0; i < clients.size(); i++){
            if (clients.get(i).getClientKey().equals(clientKey)){
                // 本地配置文件中已包含客户端的clientKey
                registeredFlag = true;
                break;
            }
        }
        if (registeredFlag){
            // 本地客户端配置文件中有此客户端
            if (ports == null) {
                logger.warn("duplicate clientKey {}, {}", clientKey, ctx.channel());
                ctx.channel().close();
                return;
            }

            Channel channel = ProxyChannelManager.getCmdChannel(clientKey);
            if (channel != null) {
                // 防止 clientKey 相同 后面的把前面的挤掉
                logger.warn("exist channel for clientKey:{}, channel:{}", clientKey, channel);
                ctx.channel().close();
                return;
            }

            logger.info("set port => channel, clientKey:{}, ports:{}, ctx.channel:{}", clientKey, ports, ctx.channel());
            ProxyChannelManager.addCmdChannel(ports, clientKey, ctx.channel());

        }else {
            // 以下为支持客户端自发现的实现 本地配置文件中没有此客户端
            ProxyConfig.Client client = new ProxyConfig.Client();
            client.setStatus(0);
            client.setTag("未注册");
            client.setName(clientKey);
            client.setClientKey(clientKey);
            client.setProxyMappings(new ArrayList<ProxyConfig.ClientProxyMapping>());

            logger.info("客户端自发现更新的配置:{}", JsonUtil.object2json(client));

            ProxyConfig.getInstance().updateAddOneClient(client);

            ctx.channel().close();
            return;
        }
    }

    /**
     * 当一个Channel的可写的状态发生改变的时候执行
     */
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel userChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();

        if (userChannel != null) {
            userChannel.config().setOption(ChannelOption.AUTO_READ, ctx.channel().isWritable());
        }

        super.channelWritabilityChanged(ctx);
    }

    /**
     * 当一个Channel已经处于非激活的状态且不再连接到远程端的时候被调用执行
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel userChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();

        if (userChannel != null && userChannel.isActive()) {
            String clientKey = ctx.channel().attr(Constants.CLIENT_KEY).get();
            String userId = ctx.channel().attr(Constants.USER_ID).get();
            Channel cmdChannel = ProxyChannelManager.getCmdChannel(clientKey);

            if (cmdChannel != null) {
                ProxyChannelManager.removeUserChannelFromCmdChannel(cmdChannel, userId);
                logger.info("removed user channel from cmd channel: userId " + userId + "clientKey:" + clientKey);
            } else {
                logger.warn("null cmdChannel, clientKey is {}", clientKey);
            }

            // 数据发送完成后再关闭连接，解决http1.0数据传输问题
            userChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            userChannel.close();
        } else {
            // channel 处于非激活状态时直接删除
            ProxyChannelManager.removeCmdChannel(ctx.channel());
        }

        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("exception caught", cause);
        super.exceptionCaught(ctx, cause);
    }
}