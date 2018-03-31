package cn.alone.rpc.util;

import cn.alone.rpc.server.RpcServer;

/**
 * Created by RojerAlone on 2017-11-14
 * 服务退出之前的钩子
 */
public class CommonHook {

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {

            }
        }));
    }

}
