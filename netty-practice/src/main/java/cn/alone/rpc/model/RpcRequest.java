package cn.alone.rpc.model;

/**
 * Created by RojerAlone on 2017-11-13
 */
public class RpcRequest {

    /**
     * 请求 ID，唯一标识一个请求
     */
    private String requestId;

    /**
     * 请求方法的类名
     */
    private String className;

    /**
     * 请求方法的名字
     */
    private String methodName;

    /**
     * 请求方法的参数类型
     */
    private Class<?>[] paramsTypes;

    /**
     * 请求方法的参数
     */
    private Object[] params;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class<?>[] getParamsTypes() {
        return paramsTypes;
    }

    public void setParamsTypes(Class<?>[] paramsTypes) {
        this.paramsTypes = paramsTypes;
    }

    public Object[] getParams() {
        return params;
    }

    public void setParams(Object[] params) {
        this.params = params;
    }
}
