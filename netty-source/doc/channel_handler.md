# Channel
Channel 是 Netty 中的核心概念之一，所有的数据都在管道中处理，而不是传统的 Java IO 中的面向数据流。开发者最常打交道的都是 `ChannelHandler`，在对 Netty 基本概念了解之后可以看一下相关的源码。

`Channel` 接口是 Netty 中对网络操作的抽象，相当于 Java NIO 中的 `SocketChannel`/`ServerSocketChannel`。

`ChannelHandler` 是一个接口，作用是对 Channel 发生的事件进行处理，是对 Channel 这个概念的抽象，而 接口 `ChannelInboundHandler` 和 `ChannelOutboundHandler` 继承 `ChannelHandler` 抽象出 Channel 的输入和输出。

依次来看下源码。
## `Channel` 接口
```java
public interface Channel extends AttributeMap, ChannelOutboundInvoker, Comparable<Channel> {

    /**
     * 获取当前管道全局唯一的 ID
     */
    ChannelId id();

    /**
     * 获取当前 Channel 绑定的 EventLoop
     */
    EventLoop eventLoop();
    
    Channel parent();
    ChannelConfig config();
    boolean isOpen();
    boolean isRegistered();
    boolean isActive();
    ChannelMetadata metadata();
    SocketAddress localAddress();
    SocketAddress remoteAddress();
    boolean isWritable();
    long bytesBeforeUnwritable();
    long bytesBeforeWritable();
    Unsafe unsafe();
    ChannelPipeline pipeline();
    ByteBufAllocator alloc();

    @Override
    Channel read();

    @Override
    Channel flush();

    /**
     * Unsafe 只应该在 Netty 内部使用而不是开发者使用，并且必须在 IO 线程中使用，但是某些方法不能使用（源码注释中有）
     */
    interface Unsafe {
        RecvByteBufAllocator.Handle recvBufAllocHandle();
        SocketAddress localAddress();
        SocketAddress remoteAddress();
        void register(EventLoop eventLoop, ChannelPromise promise);
        void bind(SocketAddress localAddress, ChannelPromise promise);
        void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);
        void disconnect(ChannelPromise promise);
        void close(ChannelPromise promise);
        void closeForcibly();
        void deregister(ChannelPromise promise);
        void beginRead();
        void write(Object msg, ChannelPromise promise);
        void flush();
        ChannelPromise voidPromise();
        ChannelOutboundBuffer outboundBuffer();
    }
}
```

## `ChannelHandler` 接口

```java
public interface ChannelHandler {

    /**
     * 当一个 ChannelHandler 被添加到 ChannelHandlerContext 并且可以处理 channel 中的事件时将会调用这个方法
     */
    void handlerAdded(ChannelHandlerContext ctx) throws Exception;

    /**
     * 当一个 ChannelHandler 从 ChannelHandlerContext 中移除并且不再处理 channel 事件时调用
     */
    void handlerRemoved(ChannelHandlerContext ctx) throws Exception;

    /**
     * 抛出异常时调用
     *
     * @deprecated 在 ChannelInboundHandler 中被抑制警告，只让 ChannelInboundHandler 使用
     */
    @Deprecated
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;

    /**
     * 标记一个 ChannelHandler 可以被添加到多个 ChannelPipeline 中
     * 如果不用这个注解标记 ChannelHandler，那么添加到其他 pipeline 的时候都要创建一个新的实例
     * 通常用在统计吞吐量的场景下，需要开发者自己保证标记的 ChannelHandler 是线程安全的
     */
    @Inherited
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Sharable {
        // no value
    }
}
```

## `ChannelInboundHandler` 接口
管道入站处理器，用来处理管道输入事件。

```java
public interface ChannelInboundHandler extends ChannelHandler {

    /**
     * 当 ChannelHandlerContext 的 Channel 被注册到 EventLoop 时调用
     */
    void channelRegistered(ChannelHandlerContext ctx) throws Exception;

    /**
     * 当 ChannelHandlerContext 的 Channel 从 EventLoop 中被移除时调用
     */
    void channelUnregistered(ChannelHandlerContext ctx) throws Exception;

    /**
     * ChannelHandlerContext 中的 Channel 激活时调用
     * 相当于 Netty4 之前的 channelOpen/channelBound/channelConnected 事件的集合
     */
    void channelActive(ChannelHandlerContext ctx) throws Exception;

    /**
     * ChannelHandlerContext 中注册的 Channel 处于非活跃状态并且生命周期即将结束的时候调用
     * 相当于 Netty4 之前的 channelClosed/channelUnbound/channelDisconnected 事件的集合
     */
    void channelInactive(ChannelHandlerContext ctx) throws Exception;

    /**
     * 当 Channel 从另一端读取到消息后调用
     */
    void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception;

    /**
     * 当 channel 中的所有数据都被读取完毕以后调用
     */
    void channelReadComplete(ChannelHandlerContext ctx) throws Exception;

    /**
     * 当一个用户事件被触发时调用
     */
    void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception;

    /**
     * 当 Channel 的 writable 状态改变时调用，可以通过 Channel.isWritable() 方法查看状态
     */
    void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception;

    /**
     * 异常发生时调用
     */
    @Override
    @SuppressWarnings("deprecation")
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;
}
```

## `ChannelOutboundHandler` 接口
管道出站处理器，用来处理管道输出事件。

```java
public interface ChannelOutboundHandler extends ChannelHandler {
    /**
     * 发生 bind 操作时调用
     */
    void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception;

    /**
     * 发生 connect 操作时调用
     */
    void connect(
            ChannelHandlerContext ctx, SocketAddress remoteAddress,
            SocketAddress localAddress, ChannelPromise promise) throws Exception;

    /**
     * 发生连接关闭时调用
     */
    void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;

    /**
     * 发生关闭操作时调用
     */
    void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;

    /**
     * 当从当前注册的 EventLoop 中注销时候调用
     */
    void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;

    /**
     * 截获了 ChannelHandlerContext.read() 操作
     */
    void read(ChannelHandlerContext ctx) throws Exception;

    /**
     * 当 write 操作发生时调用，write 操作写入的消息将会经过 ChannelPipeline，当 Channel.flush() 操作被调用时写入的消息将会被 flush 到对应的 Channel 中。
     */
    void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception;

    /**
     * 发生 flush 操作时调用，flush 操作会把之前写入的所有待处理消息 flush
     */
    void flush(ChannelHandlerContext ctx) throws Exception;
}
```

## 小结

看了源码之后，可以看出来：

- `Channel` 对应 Java NIO 中的 `SocketChannel` 和 `ServerSocketChannel`，是对网络传输的抽象
- `ChannelHandler` 是 Netty 的基石，是对管道中发生的各种事件进行处理，所有方法都是相关事件发生以后调用的
- `ChannelInboundHandler` 是入站事件处理，`ChannelOutboundHandler` 是出站事件处理，包含了在 `ChannelHandlerContext` 中的状态变化后要执行的操作