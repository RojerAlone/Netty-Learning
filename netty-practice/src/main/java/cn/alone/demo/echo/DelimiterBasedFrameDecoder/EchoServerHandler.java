package cn.alone.demo.echo.DelimiterBasedFrameDecoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Created by RojerAlone on 2017-08-15.
 */
public class EchoServerHandler extends ChannelInboundHandlerAdapter {

    int counter = 0;

    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        String body = (String) msg; // 已经被解码过的消息
        System.out.println("This is " + ++counter + " times receive client : [" + body + "]");
        body += "$_";
        ByteBuf echo = Unpooled.copiedBuffer(body.getBytes());
        ctx.writeAndFlush(echo);
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

}
