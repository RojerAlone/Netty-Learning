package cn.alone.demo.TimeServer.Netty.LineBasedFrameDecoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.UnsupportedEncodingException;
import java.util.Date;

/**
 * Created by RojerAlone on 2017-08-14.
 */
public class TimeServerHandler extends ChannelInboundHandlerAdapter {

    private int counter;

    public void channelRead(ChannelHandlerContext ctx, Object msg) throws UnsupportedEncodingException {
        String body = (String) msg;
        System.out.println("The time server receive order : " + body + " ; the counter is : " + ++counter);
        String currentTime = "QUERY TIME ORDER".equalsIgnoreCase(body) ? new Date().toString() : "BAD ORDER";
        ByteBuf resp = Unpooled.copiedBuffer(currentTime.getBytes());
        ctx.write(resp); // write 方法不直接将消息写入到 SocketChannel 中，只把待发送的消息放到发送缓冲数组中，调用 flush 才写入
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }

}
