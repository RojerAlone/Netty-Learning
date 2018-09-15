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

```java
abstract class AbstractChannelHandlerContext extends DefaultAttributeMap implements ChannelHandlerContext, ResourceLeakHint {

    // 前驱结点和后继结点
    volatile AbstractChannelHandlerContext next;
    volatile AbstractChannelHandlerContext prev;

    // 用来设置 handler 状态的更新器
    private static final AtomicIntegerFieldUpdater<AbstractChannelHandlerContext> HANDLER_STATE_UPDATER;
    
    // 当 ChannelHandler.handlerAdded 准备被调用的状态（在 Netty 中是异步的，有中间状态）
    private static final int ADD_PENDING = 1;
    // 当 ChannelHandler.handlerAdded 被调用以后的状态
    private static final int ADD_COMPLETE = 2;
    // 当 ChannelHandler.handlerRemoved 被调用以后的状态
    private static final int REMOVE_COMPLETE = 3;
    // 最初始的状态，创建一个 context 对象时候的初始状态
    private static final int INIT = 0;
    
    private final boolean inbound; // 是否是入站 handler
    private final boolean outbound; // 是否是出站 handler
    private final DefaultChannelPipeline pipeline; // 绑定的 pipeline
    private final String name; // 全局唯一的名字
    private final boolean ordered; // 事件是否是顺序执行的

    // 执行器，如果没有设置为 null
    final EventExecutor executor;
    // successFuture 的 isSuccess 总是返回 true，保证了不会阻塞
    private ChannelFuture succeededFuture;

    // 异步事件任务
    private Runnable invokeChannelReadCompleteTask;
    private Runnable invokeReadTask;
    private Runnable invokeChannelWritableStateChangedTask;
    private Runnable invokeFlushTask;

    private volatile int handlerState = INIT; // handler 初始状态为 INIT
}
```

类属性主要是前驱和后继结点，以及 handler 状态的常量、当前 handler 绑定的 pipeline、executor 和异步事件任务（Netty 中的一切都是异步的，包括事件传递）。

### 初始化

初始化有两部分，静态代码块和构造方法。

```java
static {
    AtomicIntegerFieldUpdater<AbstractChannelHandlerContext> handlerStateUpdater = PlatformDependent
            .newAtomicIntegerFieldUpdater(AbstractChannelHandlerContext.class, "handlerState");
    if (handlerStateUpdater == null) {
        handlerStateUpdater = AtomicIntegerFieldUpdater
                .newUpdater(AbstractChannelHandlerContext.class, "handlerState");
    }
    HANDLER_STATE_UPDATER = handlerStateUpdater;
}
```

静态代码块对 `handlerStateUpdater` 进行了赋值，使用了 JDK 自带的并发包内的 `AtomicIntegerFieldUpdater` 类，用于更新 handler 状态。

```java
AbstractChannelHandlerContext(DefaultChannelPipeline pipeline, EventExecutor executor, String name,
                              boolean inbound, boolean outbound) {
    this.name = ObjectUtil.checkNotNull(name, "name");
    this.pipeline = pipeline;
    this.executor = executor;
    this.inbound = inbound;
    this.outbound = outbound;
    // 如果 executor 是 null 或者是 OrderedEventExecutor 类型，事件驱动是有序的
    ordered = executor == null || executor instanceof OrderedEventExecutor;
}
```

构造方法只对类属性做了赋值。

### 核心功能：事件传递

事件传递是在 pipeline 中被触发的，但是实际的传递过程是在 context 进行的，通过双向链表一个一个沿事件流动方向传递。

`ChannelHandlerContext` 接口继承自 `ChannelInboundInvoker` 接口的事件传递方法都是以 `fire***`格式命名的方法，以 `fireChannelRead` 方法为例：

```java
@Override
public ChannelHandlerContext fireChannelRead(final Object msg) {
    invokeChannelRead(findContextInbound(), msg); // findContextInbound 找到下一个入站 handler
    return this;
}

private AbstractChannelHandlerContext findContextInbound() {
    AbstractChannelHandlerContext ctx = this;
    do {
        ctx = ctx.next;
    } while (!ctx.inbound);
    return ctx;
}

static void invokeChannelRead(final AbstractChannelHandlerContext next, Object msg) {
    final Object m = next.pipeline.touch(ObjectUtil.checkNotNull(msg, "msg"), next); // touch 用于内存泄漏的抽样检测，如果没有设置检测就返回 msg
    EventExecutor executor = next.executor();
    if (executor.inEventLoop()) { // 如果当前线程是 eventLoop 线程，执行 invoke
        next.invokeChannelRead(m);
    } else {
        executor.execute(new Runnable() { // 提交到线程池
            @Override
            public void run() {
                next.invokeChannelRead(m);
            }
        });
    }
}

private void invokeChannelRead(Object msg) {
    if (invokeHandler()) { // 如果当前 context 绑定的 handler 已经可以使用了，调用 handler 的 channelRead 方法
        try {
            ((ChannelInboundHandler) handler()).channelRead(this, msg);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    } else { // 如果 handler 状态还不是 ADD_COMPLETE，继续往下传递
        fireChannelRead(msg);
    }
}
```

从源码中可以看到，事件只是在两个 `ChannelHandlerContext` 之间传递(从当前 context 到下一个 context)，如果想传递至整个 pipeline 中，需要开发者在自己的重写 `ChannelInboundHanlder` 的 `channel***` 方法中调用 `fire***` 方法让事件继续传递。

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

