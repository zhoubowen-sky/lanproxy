package com.xbrother.lanproxy.server.handlers;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;

import com.xbrother.lanproxy.protocol.Constants;
import com.xbrother.lanproxy.protocol.ProxyMessage;
import com.xbrother.lanproxy.server.ProxyChannelManager;
import com.xbrother.lanproxy.server.config.ProxyConfig;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 处理服务端 channel.
 */
public class UserChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static AtomicLong userIdProducer = new AtomicLong(0);

    private static Logger logger = LoggerFactory.getLogger(UserChannelHandler.class);

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 当出现异常就关闭连接
        ctx.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        // 通知代理客户端
        Channel userChannel = ctx.channel();
        Channel proxyChannel = userChannel.attr(Constants.NEXT_CHANNEL).get();

        if (proxyChannel == null) {
            logger.warn("proxyChannel == null 该端口还没有代理");
            // 该端口还没有代理客户端
            ctx.channel().close();
        } else {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            String userId = ProxyChannelManager.getUserChannelUserId(userChannel);

            ProxyMessage proxyMessage = new ProxyMessage();
            proxyMessage.setType(ProxyMessage.P_TYPE_TRANSFER);
            proxyMessage.setUri(userId);
            proxyMessage.setData(bytes);

            proxyChannel.writeAndFlush(proxyMessage);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel userChannel = ctx.channel();
        InetSocketAddress sa = (InetSocketAddress) userChannel.localAddress();
        Channel cmdChannel = ProxyChannelManager.getCmdChannel(sa.getPort());

        if (cmdChannel == null) {
            // 该端口还没有代理客户端
            ctx.channel().close();
        } else {
            String userId = newUserId();
            String lanInfo = ProxyConfig.getInstance().getLanInfo(sa.getPort());
            // 用户连接到代理服务器时，设置用户连接不可读，等待代理后端服务器连接成功后再改变为可读状态
            userChannel.config().setOption(ChannelOption.AUTO_READ, false);
            ProxyChannelManager.addUserChannelToCmdChannel(cmdChannel, userId, userChannel);

            ProxyMessage proxyMessage = new ProxyMessage();
            proxyMessage.setType(ProxyMessage.TYPE_CONNECT);
            proxyMessage.setUri(userId);
            proxyMessage.setData(lanInfo.getBytes());
            cmdChannel.writeAndFlush(proxyMessage);
        }

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {

        // 通知代理客户端
        Channel userChannel = ctx.channel();
        InetSocketAddress sa = (InetSocketAddress) userChannel.localAddress();
        Channel cmdChannel = ProxyChannelManager.getCmdChannel(sa.getPort());
        if (cmdChannel == null) {
            // 该端口还没有代理客户端
            ctx.channel().close();
        } else {
            // 用户连接断开，从控制连接中移除
            String userId = ProxyChannelManager.getUserChannelUserId(userChannel);
            ProxyChannelManager.removeUserChannelFromCmdChannel(cmdChannel, userId);
            Channel proxyChannel = userChannel.attr(Constants.NEXT_CHANNEL).get();

            if (proxyChannel != null && proxyChannel.isActive()) {
                /*
                proxyChannel.attr(Constants.NEXT_CHANNEL).remove();
                proxyChannel.attr(Constants.CLIENT_KEY).remove();
                proxyChannel.attr(Constants.USER_ID).remove();
                */

                proxyChannel.attr(Constants.NEXT_CHANNEL).set(null);
                proxyChannel.attr(Constants.CLIENT_KEY).set(null);
                proxyChannel.attr(Constants.USER_ID).set(null);


                proxyChannel.config().setOption(ChannelOption.AUTO_READ, true);
                // 通知客户端，用户连接已经断开
                ProxyMessage proxyMessage = new ProxyMessage();
                proxyMessage.setType(ProxyMessage.TYPE_DISCONNECT);
                proxyMessage.setUri(userId);
                proxyChannel.writeAndFlush(proxyMessage);
            }
        }

        super.channelInactive(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {

        // 通知代理客户端
        Channel userChannel = ctx.channel();
        InetSocketAddress sa = (InetSocketAddress) userChannel.localAddress();
        Channel cmdChannel = ProxyChannelManager.getCmdChannel(sa.getPort());
        if (cmdChannel == null) {
            // 该端口还没有代理客户端
            ctx.channel().close();
        } else {
            Channel proxyChannel = userChannel.attr(Constants.NEXT_CHANNEL).get();
            if (proxyChannel != null) {
                proxyChannel.config().setOption(ChannelOption.AUTO_READ, userChannel.isWritable());
            }
        }

        super.channelWritabilityChanged(ctx);
    }

    /**
     * 为用户连接产生ID
     *
     * @return
     */
    private static String newUserId() {
        return String.valueOf(userIdProducer.incrementAndGet());
    }
}