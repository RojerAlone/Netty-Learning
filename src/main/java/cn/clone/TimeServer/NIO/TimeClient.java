package cn.clone.TimeServer.NIO;

/**
 * Created by RojerAlone on 2017-08-14.
 */
public class TimeClient {

    private static final int PORT = 9090;

    public static void main(String[] args) {
        new Thread(new TimeClientHandle("127.0.0.1", PORT), "TimeClient-001").start();
    }

}
