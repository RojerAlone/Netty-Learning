package cn.alone.rpc.server;

import cn.alone.rpc.config.RpcServerConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * Created by RojerAlone on 2017-11-14
 * rpc 服务器
 */
public class RpcServer {

    private static EventLoopGroup bossGroup = new NioEventLoopGroup();
    private static EventLoopGroup workerGroup = new NioEventLoopGroup();

    public static void init() {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                RpcServer.stop();
            }
        }));
    }

    public static void start() throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap();

        try {
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    // 客户端请求时候服务端只能接收一个连接请求， SO_BACKLOG 指定等待队列的大小
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {

                        }
                    });
            ChannelFuture future = bootstrap.bind(RpcServerConfig.SERVER_PORT).sync();
            System.out.println("rpc server start on port " + RpcServerConfig.SERVER_PORT);
            future.channel().closeFuture().sync();
        } finally {
            stop();
        }
    }

    /**
     * 关闭 Netty 服务
     */
    private static void stop() {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

}
