package cn.alone.rpc.client;

import cn.alone.rpc.model.RpcRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

/**
 * Created by RojerAlone on 2017-11-13
 * 动态代理透明化客户端操作
 */
public class RpcProxyClient implements InvocationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcProxyClient.class);

    public static <T> T getInstance(Class<?> clazz) {
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, new RpcProxyClient());
    }

    @Override
    public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setClassName(o.getClass().getName());
        request.setMethodName(method.getName());
        request.setParamsTypes(method.getParameterTypes());
        request.setParams(objects);

        Object obj = RpcClientHandler.handle(request); // 向服务器请求
        return obj;
    }

}
