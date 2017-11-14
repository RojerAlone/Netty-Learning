package cn.alone.rpc.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by RojerAlone on 2017-11-14
 * rpc 服务启动器
 */
public class RpcServerStarter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcServerStarter.class);

    public static void main(String[] args) {
        try {
            RpcServer.start();
        } catch (Exception e) {
            LOGGER.error("rpc start failed : ", e);
            System.exit(-1);
        }
    }

}
