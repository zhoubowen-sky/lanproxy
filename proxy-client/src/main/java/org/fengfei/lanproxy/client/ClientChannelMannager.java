package org.fengfei.lanproxy.client;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.fengfei.lanproxy.client.listener.ProxyChannelBorrowListener;
import org.fengfei.lanproxy.common.Config;
import org.fengfei.lanproxy.protocol.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.util.AttributeKey;

/**
 * 代理客户端与后端真实服务器连接管理
 * 一个客户端可以代理很多个端口 每个端口都可以建立映射关系
 */
public class ClientChannelMannager {

    private static Logger logger = LoggerFactory.getLogger(ClientChannelMannager.class);

    private static final AttributeKey<Boolean> USER_CHANNEL_WRITEABLE = AttributeKey.newInstance("user_channel_writeable");

    private static final AttributeKey<Boolean> CLIENT_CHANNEL_WRITEABLE = AttributeKey.newInstance("client_channel_writeable");

    private static final int MAX_POOL_SIZE = 100;

    private static Map<String, Channel> realServerChannels = new ConcurrentHashMap<String, Channel>(); // String 为 ProxyMessage 中的 userId 客户端唯一ID

    private static ConcurrentLinkedQueue<Channel> proxyChannelPool = new ConcurrentLinkedQueue<Channel>(); // 非阻塞式的安全队列

    private static volatile Channel cmdChannel;

    private static Config config = Config.getInstance();

    public static void borrowProxyChannel(Bootstrap bootstrap, final ProxyChannelBorrowListener borrowListener) {
        Channel channel = proxyChannelPool.poll(); // 获取元素 并在队列中清除
        if (channel != null) {
            borrowListener.success(channel);
            logger.info("proxyChannelPool.poll() success");
            return;
        }

        // 如果队列中没有连接 说明是初始启动状态 因此新增一个连接 此处新增一个 listener
        // Netty 为异步IO操作 通过重写 operationComplete 方法执行异步IO操作完成后的动作
        bootstrap.connect(config.getStringValue("server.host"), config.getIntValue("server.port")).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    borrowListener.success(future.channel());
                } else {
                    logger.warn("connect proxy server failed", future.cause());
                    borrowListener.error(future.cause());
                }
            }
        });
    }

    public static void returnProxyChannel(Channel proxyChanel) {
        // 此处为何作此限制？一个客户端映射的连接超过 100 会怎样？
        if (proxyChannelPool.size() > MAX_POOL_SIZE) {
            proxyChanel.close();
        } else {
            proxyChanel.config().setOption(ChannelOption.AUTO_READ, true);
            /*
            proxyChanel.attr(Constants.NEXT_CHANNEL).remove();
            */
            proxyChanel.attr(Constants.NEXT_CHANNEL).set(null);
            // 在队列最后插入元素
            proxyChannelPool.offer(proxyChanel);
            logger.debug("return ProxyChanel to the pool, channel is {}, pool size is {} ", proxyChanel, proxyChannelPool.size());
        }
    }

    public static void removeProxyChanel(Channel proxyChanel) {
        proxyChannelPool.remove(proxyChanel);
    }

    public static void setCmdChannel(Channel cmdChannel) {
        ClientChannelMannager.cmdChannel = cmdChannel;
    }

    public static Channel getCmdChannel() {
        return cmdChannel;
    }

    public static void setRealServerChannelUserId(Channel realServerChannel, String userId) {
        realServerChannel.attr(Constants.USER_ID).set(userId);
    }

    public static String getRealServerChannelUserId(Channel realServerChannel) {
        return realServerChannel.attr(Constants.USER_ID).get();
    }

    public static Channel getRealServerChannel(String userId) {
        return realServerChannels.get(userId);
    }

    public static void addRealServerChannel(String userId, Channel realServerChannel) {
        realServerChannels.put(userId, realServerChannel);
    }

    public static Channel removeRealServerChannel(String userId) {
        return realServerChannels.remove(userId);
    }

    public static boolean isRealServerReadable(Channel realServerChannel) {
        return realServerChannel.attr(CLIENT_CHANNEL_WRITEABLE).get() && realServerChannel.attr(USER_CHANNEL_WRITEABLE).get();
    }

    public static void clearRealServerChannels() {
        logger.warn("channel closed, clear real server channels");

        Iterator<Entry<String, Channel>> ite = realServerChannels.entrySet().iterator();
        while (ite.hasNext()) {
            Channel realServerChannel = ite.next().getValue();
            if (realServerChannel.isActive()) {
                realServerChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }

        realServerChannels.clear();
    }
}
