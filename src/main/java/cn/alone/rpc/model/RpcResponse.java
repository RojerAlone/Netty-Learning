package cn.alone.rpc.model;

/**
 * Created by RojerAlone on 2017-11-13
 */
public class RpcResponse {

    /**
     * 请求的 ID
     */
    private String requestId;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 请求的结果
     */
    private Object result;

    public String getRequestId() {
        return requestId;
    }

    public String getError() {
        return error;
    }

    public Object getResult() {
        return result;
    }
}
