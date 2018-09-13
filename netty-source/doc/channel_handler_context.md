# ChannelHandlerContext
`ChannelHandlerContext` 是 `ChannelHandler` 和 `ChannelPipeline` 交互的纽带，提供了在 `ChannelPipeline` 中传递事件的功能，以及获取所绑定的 pipeline 和 channel 的能力。

## ChannelHandlerContext 接口
`ChannelHandlerContext` 是一个接口，代码如下：

```java
public interface ChannelHandlerContext extends AttributeMap, ChannelInboundInvoker, ChannelOutboundInvoker {

    Channel channel(); // 获取绑定的 channel
    EventExecutor executor(); // 获取绑定的 EventExecutor
    String name(); // 全局唯一的名字，其实就是 ChannelHandler 的名字
    ChannelHandler handler(); // 对应的 ChannelHandler
    boolean isRemoved(); // 当前 context 是否从 pipeline 中移除了

    // 在 ChannelHandlerContext 中重写了父接口 ChannelInboundInvoker 的方法，没有功能上的意义，只是为了增加可读性，不在一一列出了
    @Override
    ChannelHandlerContext fire***();

    // 同上，重写 ChannelOutboundInvoker 的方法
    @Override
    ChannelHandlerContext read();

    @Override
    ChannelHandlerContext flush();

    ChannelPipeline pipeline(); // 对应的 pipeline
    ByteBufAllocator alloc(); // 用来分配 ByteBuf 的 allocator

    // 继承了 AttributeMap 的方法，已废弃，应该使用 channel 中对应的方法
    @Deprecated
    @Override
    <T> Attribute<T> attr(AttributeKey<T> key);

    @Deprecated
    @Override
    <T> boolean hasAttr(AttributeKey<T> key);
}
```

## AbstractChannelHandlerContext 抽象类

`AbstractChannelHandlerContext` 实现了 `ChannelHandlerContext` 接口中的大多数方法，最终的 `ChannelHandlerContext` 接口实现类都继承了这个抽象类。

### 类属性

### 初始化

### 核心功能：事件传递

## DefaultChannelHandlerContext

`DefaultChannelHandlerContext` 是默认的 `ChannelHandlerContext` 实现，继承 `AbstractChannelHandlerContext` 抽象类，`ChannelHanlderContext` 的功能都已经在抽象类中实现了，`DefaultChannelHandlerContext` 只是传入了 `ChannelHandler`，并且判断 handler 的类型。

```java
final class DefaultChannelHandlerContext extends AbstractChannelHandlerContext {

    private final ChannelHandler handler;

    DefaultChannelHandlerContext(
            DefaultChannelPipeline pipeline, EventExecutor executor, String name, ChannelHandler handler) {
        super(pipeline, executor, name, isInbound(handler), isOutbound(handler)); // AbstractChannelHandlerContext 的构造方法
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        this.handler = handler;
    }

    @Override
    public ChannelHandler handler() {
        return handler;
    }
    // 是否是入站 handler
    private static boolean isInbound(ChannelHandler handler) {
        return handler instanceof ChannelInboundHandler;
    }
    // 是否是出站 handler
    private static boolean isOutbound(ChannelHandler handler) {
        return handler instanceof ChannelOutboundHandler;
    }
}
```

## HeadContext

`HeadContext` 是 `ChannelHandlerPipeline` 中的头结点，既是入站事件的第一个 handler，也是出站的最后一个 handler，在这个 handler 中实现了对网络 IO 的处理。

```java
final class HeadContext extends AbstractChannelHandlerContext implements ChannelOutboundHandler, ChannelInboundHandler {

    private final Unsafe unsafe; // Netty 内部的工具，不对外暴露，用来和 java nio api 交互 IO 相关事件

    HeadContext(DefaultChannelPipeline pipeline) {
        super(pipeline, null, HEAD_NAME, false, true); // 调用 AbstractChannelHanlderContext 的构造方法
        unsafe = pipeline.channel().unsafe(); // 获取当前 pipeline 对应的 channel 所绑定的 unsafe 对象
        setAddComplete(); // 设置当前 handler（即自己）的状态为 ADD_COMPLETE
    }
}
```

## TailContext

`TailContext` 是入站事件的最后一个 handler，只做了简单的处理。

```java
final class TailContext extends AbstractChannelHandlerContext implements ChannelInboundHandler {

    TailContext(DefaultChannelPipeline pipeline) {
        super(pipeline, null, TAIL_NAME, true, false);
        setAddComplete();
    }
    
    // 略过 ChannelInboundHandler 的方法，TailContext 什么也没做，就是个打酱油的
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception { 
        ReferenceCountUtil.release(evt); // 合理的逻辑，不需要打印日志，只释放资源
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        onUnhandledInboundException(cause); // 打印 warn 日志并释放资源
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        onUnhandledInboundMessage(msg); // 打印 debug 日志并释放资源
    }
}
```

`TailContext` 从 `ChannelInboundHandler` 的方法的实现就是什么也不干，只不过在 `exceptionCaught`、`exceptionCaught` 和 `channelRead` 方法中释放了资源，在 `exceptionCaught` 和 `channelRead` 方法中调用 `DefaultChannelHandlerPipeline` 的方法分别打印了 warn 和 debug 级别的日志以及释放引用。

```java
protected void onUnhandledInboundException(Throwable cause) {
    try {
        // 告诉开发者最后一个 handler 没有处理异常，并且打印 exception cause
        logger.warn(
                    "An exceptionCaught() event was fired, and it reached at the tail of the pipeline. " +
                    "It usually means the last handler in the pipeline did not handle the exception.",
                    cause);
    } finally {
        ReferenceCountUtil.release(cause);
    }
}
```

```java
protected void onUnhandledInboundMessage(Object msg) {
     try {
        // 只是 handler 没有处理消息，打印 debug 级别的日志
        logger.debug(
                    "Discarded inbound message {} that reached at the tail of the pipeline. " +
                            "Please check your pipeline configuration.", msg);
     } finally {
         ReferenceCountUtil.release(msg);
     }
}
```

