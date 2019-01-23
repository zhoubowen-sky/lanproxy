package com.xbrother.lanproxy.server;

import java.net.BindException;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import com.xbrother.lanproxy.server.config.web.WebConfigContainer;
import com.xbrother.lanproxy.server.handlers.ServerChannelHandler;
import com.xbrother.lanproxy.server.handlers.UserChannelHandler;
import com.xbrother.lanproxy.server.metrics.handler.BytesMetricsHandler;
import com.xbrother.lanproxy.common.Config;
import com.xbrother.lanproxy.common.container.Container;
import com.xbrother.lanproxy.common.container.ContainerHelper;
import com.xbrother.lanproxy.protocol.IdleCheckHandler;
import com.xbrother.lanproxy.protocol.ProxyMessageDecoder;
import com.xbrother.lanproxy.protocol.ProxyMessageEncoder;
import com.xbrother.lanproxy.server.config.ProxyConfig;
import com.xbrother.lanproxy.server.config.ProxyConfig.ConfigChangedListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslHandler;

public class ProxyServerContainer implements Container, ConfigChangedListener {

    /**
     * max packet is 2M.
     */
    private static final int MAX_FRAME_LENGTH = 2 * 1024 * 1024;

    private static final int LENGTH_FIELD_OFFSET = 0;

    private static final int LENGTH_FIELD_LENGTH = 4;

    private static final int INITIAL_BYTES_TO_STRIP = 0;

    private static final int LENGTH_ADJUSTMENT = 0;

    private static Logger logger = LoggerFactory.getLogger(ProxyServerContainer.class);

    private NioEventLoopGroup serverWorkerGroup;

    private NioEventLoopGroup serverBossGroup;

    public ProxyServerContainer() {

        serverBossGroup = new NioEventLoopGroup();
        serverWorkerGroup = new NioEventLoopGroup();

        ProxyConfig.getInstance().addConfigChangedListener(this);
    }

    @Override
    public void start() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new ProxyMessageDecoder(MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH, LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP));
                ch.pipeline().addLast(new ProxyMessageEncoder());
                ch.pipeline().addLast(new IdleCheckHandler(IdleCheckHandler.READ_IDLE_TIME, IdleCheckHandler.WRITE_IDLE_TIME, 0));
                ch.pipeline().addLast(new ServerChannelHandler());
            }
        });

        try {
            bootstrap.bind(ProxyConfig.getInstance().getServerBind(), ProxyConfig.getInstance().getServerPort()).get();
            logger.info("proxy server start on port " + ProxyConfig.getInstance().getServerPort());

        } catch (Exception ex) {
            logger.error("proxy server start failed:" + ex.getMessage());
            throw new RuntimeException(ex);
        }

        if (Config.getInstance().getBooleanValue("server.ssl.enable", false)) {
            String host = Config.getInstance().getStringValue("server.ssl.bind", "0.0.0.0");
            int port = Config.getInstance().getIntValue("server.ssl.port");
            initializeSSLTCPTransport(host, port, new SslContextCreator().initSSLContext());
        }

        // 连同已经配置了的客户端端口一同启动 且与上述主程序参数不同
        startUserPort();

    }

    private void initializeSSLTCPTransport(String host, int port, final SSLContext sslContext) {
        ServerBootstrap b = new ServerBootstrap();
        b.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                try {
                    pipeline.addLast("ssl", createSslHandler(sslContext, Config.getInstance().getBooleanValue("server.ssl.needsClientAuth", false)));
                    ch.pipeline().addLast(new ProxyMessageDecoder(MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH, LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP));
                    ch.pipeline().addLast(new ProxyMessageEncoder());
                    ch.pipeline().addLast(new IdleCheckHandler(IdleCheckHandler.READ_IDLE_TIME, IdleCheckHandler.WRITE_IDLE_TIME, 0));
                    ch.pipeline().addLast(new ServerChannelHandler());
                } catch (Throwable th) {
                    logger.error("Severe error during pipeline creation", th);
                    throw th;
                }
            }
        });
        try {
            // Bind and start to accept incoming connections.
            ChannelFuture f = b.bind(host, port);
            f.sync();
            logger.info("proxy ssl server start on port {}", port);
        } catch (InterruptedException ex) {
            logger.error("An interruptedException was caught while initializing server", ex);
        }
    }

    private void startUserPort() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addFirst(new BytesMetricsHandler());
                ch.pipeline().addLast(new UserChannelHandler());
            }
        });

        List<Integer> ports = ProxyConfig.getInstance().getUserPorts();
        // 被禁用的端口会被过滤掉
        for (int port : ports) {
            try {
                bootstrap.bind(port).get();
                logger.warn("bind user port " + port);
            } catch (Exception ex) {
                // BindException表示该端口已经绑定过
                if (!(ex.getCause() instanceof BindException)) {
                    logger.error("程序启动报错:{}", ex);
                    throw new RuntimeException(ex);
                }else {
                    logger.warn("端口已经绑定过了:"+port);
                }
            }
        }
    }

    @Override
    public void onChanged() {
        startUserPort();
    }

    @Override
    public void stop() {
        serverBossGroup.shutdownGracefully();
        serverWorkerGroup.shutdownGracefully();
    }

    private ChannelHandler createSslHandler(SSLContext sslContext, boolean needsClientAuth) {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(false);
        if (needsClientAuth) {
            sslEngine.setNeedClientAuth(true);
        }

        return new SslHandler(sslEngine);
    }

    public static void main(String[] args) {
        ContainerHelper.start(Arrays.asList(new Container[] { new ProxyServerContainer(), new WebConfigContainer() }));
    }

}
