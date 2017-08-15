package cn.alone.demo.TimeServer.Netty.LineBasedFrameDecoder;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;

/**
 * Created by RojerAlone on 2017-08-14.
 */
public class NettyTimeServer {

    private class ChildChannelHandler extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ch.pipeline().addLast(new LineBasedFrameDecoder(1024));
            ch.pipeline().addLast(new StringDecoder());
            ch.pipeline().addLast(new TimeServerHandler());
        }
    }

    public void bind(int port) throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup(); // 负责请求的accept操作
        EventLoopGroup workerGroup = new NioEventLoopGroup(); // 负责请求的 read、write 和处理操作
        ServerBootstrap bootstrap = new ServerBootstrap(); // Netty 用于启动 NIO 服务端的辅助启动类
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class) // NioServerSocketChannel 相当于 Java NIO 中的 ServerSocketChannel
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childHandler(new ChildChannelHandler()); // 处理网络 IO 事件
        try {
            ChannelFuture future = bootstrap.bind(port).sync(); // 绑定端口，同步等待绑定成功，成功后返回返回一个 future
            System.out.println("Time server start at port : " + PORT);
            future.channel().closeFuture().sync(); // 等待服务端监听端口关闭
        } finally { // 优雅释放资源
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private static final int PORT = 9090;

    public static void main(String[] args) throws Exception {
        new NettyTimeServer().bind(PORT);
    }

}
