package cn.clone.TimeServer.AIO;

/**
 * Created by RojerAlone on 2017-08-14.
 */
public class AsyncTimeServer {

    private static final int PORT = 9090;

    public static void main(String[] args) {
        AsyncTimeServerHandler timeServer = new AsyncTimeServerHandler(PORT);
        new Thread(timeServer, "AIO-AsyncTimeServerHandler-001").start();
    }

}
