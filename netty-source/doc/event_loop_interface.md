# EventLoop

Netty 是一个事件驱动的框架，它的核心概念之一就是线程模型，由两个线程池分别处理不同的事件：一个处理来自客户端的连接，一个处理连接中传过来的 IO 数据。

这次来看下 Netty 中线程池相关的两个核心类：`EventLoop` 和 `EventLoopGroup`。

顾名思义，`EventLoopGroup` 是一组 `EventLoop` 的集合，先从 `EventLoop` 看起。

## EventLoop 接口

`EventLoop` 接口源码如下：

```java
public interface EventLoop extends OrderedEventExecutor, EventLoopGroup {
    @Override
    EventLoopGroup parent();
}
```

可以看出 `EventLoop` 接口继承了 `OrderedEventExecutor` 和 `EventLoopGroup` 接口，接口中只有一个方法 `parent()`，返回结果是 `EventLoopGroup`　对象，重写这个方法只是为了突出它，它的 parent 是 `EventLoopGroup`。

来看下具体的继承关系图（IDEA 自动生成的图片）：

![EventLoop 接口继承图](https://github.com/RojerAlone/Netty-Learning/blob/master/netty-source/images/event_loop.png)

从图中可以看出：

- `EventLoop` 继承了 Netty 中的 `EventExecutorGroup` 接口，而 `EventExecutorGroup` 接口继承了 JDK 自带的 `ScheduledExecutorService` 接口，可以看出 Netty 扩展了 Java 中的自带的线程池。
- `EventLoopGroup` 继承了 `EventLoopGroup`，实际上 `EventLoopGroup` 是一个拥有多个线程的线程池，而 `EventLoop` 是一个 `SingleThreadPool`，它们虽然是继承关系，但是都是线程池。
- Netty 高性能的一个很重要的原因就是排除了同步的影响，一个 `Channel` 中的事件都由同一个线程来处理，一个 `EventLoopGroup` 中有多个 `EventLoop`，那么如何能知道一个 `Channel` 应该由哪个 `EventLoop` 来处理呢？`EventLoop` 继承的 `EventExecutor` 接口提供了这个方法 `boolean inEventLoop();`
- `EventLoop` 又继承了 `OrderedEventExecutor`，这个接口中没有自己的方法，只是用来标识一个 executor 是顺序执行任务的，`ChannelHandlerContext` 中的 ordered 属性就是根据这个接口判断的。

## EventExecutor 接口

`EventExecutor` 接口是 `EventLoop` 继承的接口，`EventLoop` 和 `EventLoopGroup` 不同的地方就在这个接口中。

```java
public interface EventExecutor extends EventExecutorGroup {

    // 返回自己的引用
    @Override
    EventExecutor next();

    // 返回父 EventExecutorGroup
    EventExecutorGroup parent();

    // 判断当前线程是否被当前 event loop 执行
    boolean inEventLoop();

    // 传入的参数线程是否属于当前的 event loop 去执行
    boolean inEventLoop(Thread thread);

    // 创建一个新的 Promise
    <V> Promise<V> newPromise();
    <V> ProgressivePromise<V> newProgressivePromise();

    // 返回一个已经被标记为 success 的 Future，所有加到这个 Future 的 listener 将会立马执行
    <V> Future<V> newSucceededFuture(V result);

    // 和 newSucceededFuture 相反
    <V> Future<V> newFailedFuture(Throwable cause);
}
```

## EventExecutorGroup 接口

`EventExecutor` 接口继承了 `EventExecutorGroup` 接口，同时这个接口也是 `EventLoopGroup` 接口的父接口，它也是 Netty 自己的线程池和 JDK 自带线程池的纽带，来看下源码：

```java
public interface EventExecutorGroup extends ScheduledExecutorService, Iterable<EventExecutor> {

    // 当且仅当当前 EventExecutorGroup 管理的 EventExecutor 都已经被调用 shutdownGracefully 以后或者继承自 JDK 的 ExecutorService.isShutdown 为 true 的时候返回 true
    boolean isShuttingDown();

    // shutdownGracefully(long, long, TimeUnit) 传入默认值的方法
    Future<?> shutdownGracefully();

    // 这个方法调用以后，isShuttingDown 方法返回 true，并且 executor 准备 shutdown 它自己
    // 优雅关闭的含义在于，关闭时候有个 静默期，及 quietPeriod 参数，executor 保证在关闭之前的静默期内没有任务提交，如果静默期有任务提交，那么将会执行任务，并且静默期重新开始计时
    Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit);

    // 当自己所拥有的所有的 executor 被关闭以后，将会通知返回的 future
    Future<?> terminationFuture();

    // 从线程池继承过来的方法，不建议使用，而应该用 shutdownGracefully
    @Override
    @Deprecated
    void shutdown();

    // 同上
    @Override
    @Deprecated
    List<Runnable> shutdownNow();

    // 返回一个被自己管理的 executor
    EventExecutor next();

    @Override
    Iterator<EventExecutor> iterator();

    // 以下方法都是继承自 JDK 自带线程池的方法， 不过返回结果却是 Netty 自己的 Future，这些 Future 都继承了 JDK 自带的对应的 Future
    @Override
    Future<?> submit(Runnable task);
    @Override
    <T> Future<T> submit(Runnable task, T result);
    @Override
    <T> Future<T> submit(Callable<T> task);
    @Override
    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);
    @Override
    <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);
    @Override
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);
    @Override
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);
}
```

简而言之，`EventExecutorGroup` 接口继承了线程池接口中的方法，将任务提交返回的 Future 重写为自己的 Future，并且添加的优雅关闭线程池的方法。

## EventLoopGroup 接口

`EventLoopGroup` 也是线程池，管理着 `EventLoop`，来看源码。

```java
public interface EventLoopGroup extends EventExecutorGroup {
    // 继承自 EventExecutorGroup
    @Override
    EventLoop next();

    // 注册一个 Channel 到一个 EventLoop 上，注册完成后返回的 ChannelFuture 将会得到通知
    ChannelFuture register(Channel channel);

    // 使用 ChannelPromise 注册
    ChannelFuture register(ChannelPromise promise);

    // 已废弃，使用 register(ChannelPromise promise)
    @Deprecated
    ChannelFuture register(Channel channel, ChannelPromise promise);
}
```

`EventLoopGroup` 的源码很简单，只添加了注册 `Channel` 的方法，由 `EventLoopGroup` 分配给 `Channel` 一个 `EventLoop`。

## 总结

通过前面的分析已经知道 `EventLoop`、`EventLoopGroup`、`EventExecutor` 和 `EventExecutorGroup` 都是线程池，只不过不同的接口有自己特有的方法，并且它们是有联系的。

- `EventExecutor` 用来执行任务，它是由 `EventExecutorGroup` 来管理的，`EventLoopGroup` 也继承了 `EventExecutorGroup`，`EventLoop` 同时继承了 `EventLoopGroup` 和 `EventExecutor`。
- `EventExecutor` 提供了获取 parent `EventExecutorGroup` 的方法，同时根据 Netty 的线程模型提供了 `inEventLoop` 方法判断是否当前线程应该执行方法。
- `EventLoopGroup` 提供了 `Channel` 注册的方法，由 `EventLoopGroup` 给 `Channel` 分配一个 `EventLoop`。