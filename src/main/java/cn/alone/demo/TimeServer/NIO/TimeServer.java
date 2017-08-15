package cn.alone.demo.TimeServer.NIO;

/**
 * Created by RojerAlone on 2017-08-13.
 */
public class TimeServer {

    private static final int PORT = 9090;

    public static void main(String[] args) {

        MultiplexerTimeServer timeServer = new MultiplexerTimeServer(PORT);
        new Thread(timeServer, "NIO-MultiplexerTimeServer-001").start();
    }

}
