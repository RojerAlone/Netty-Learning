package cn.alone.demo.TimeServer.AIO;

/**
 * Created by RojerAlone on 2017-08-14.
 */
public class AsyncTimeClient {

    private static final int PORT = 9090;

    public static void main(String[] args) {
        new Thread(new AsyncTimeClientHandler("127.0.0.1", PORT)).start();
    }

}
