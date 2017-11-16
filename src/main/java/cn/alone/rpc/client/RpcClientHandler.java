package cn.alone.rpc.client;

import cn.alone.rpc.config.RpcClientConfig;
import cn.alone.rpc.handler.RpcSendHandler;
import cn.alone.rpc.model.RpcRequest;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by RojerAlone on 2017-11-14
 * rpc 网络通信发送方
 */
public class RpcClientHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcClientHandler.class);
    private static boolean isStarted = false;

    private static EventLoopGroup group = new NioEventLoopGroup();
    private static Bootstrap bootstrap = new Bootstrap();

    private static void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                group.shutdownGracefully();
            }
        }));
        try {
            bootstrap.group(group).channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new RpcSendHandler());
                        }
                    });
            ChannelFuture future = bootstrap.connect(RpcClientConfig.SERVER_HOST, RpcClientConfig.SERVER_PORT).sync();
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            LOGGER.error("net connect failed", e);
        } finally {
            group.shutdownGracefully();
        }
    }

    public static Object handle(RpcRequest request) {
        if (!isStarted) { // 如果客户端还没有启动，启动客户端
            synchronized (RpcClientHandler.class) {
                if (!isStarted) {
                    start();
                }
            }
        }
        return new Object();
    }

}
