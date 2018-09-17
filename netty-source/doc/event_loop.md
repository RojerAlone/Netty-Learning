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

