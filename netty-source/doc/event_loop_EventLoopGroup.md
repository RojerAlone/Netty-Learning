# EventLoop 及其相关接口的实现 之 EventLoopGroup

`EventLoopGroup` 是开发者一定会用到的类，它继承了 `EventExecutorGroup`，是一个线程池，同时提供了注册 `Channel` 的功能。

`EventLoopGroup` 根据不同的 IO 类型有不同的实现类，比如 `OioEventLoopGroup`、`NioEventLoopGroup` 等。先从抽象类 `AbstractEventLoopGroup` 开始看。

## AbstractEventLoopGroup

看了以后发现它什么也没做。

```java
public abstract class AbstractEventLoopGroup extends AbstractEventExecutorGroup implements EventLoopGroup {
    @Override
    public abstract EventLoop next();
}
```

## MultithreadEventLoopGroup

这个类才是 `EventLoopGroup` 的抽象基类，一个多线程的 `EventLoopGroup`。

```java
public abstract class MultithreadEventLoopGroup extends MultithreadEventExecutorGroup implements EventLoopGroup {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MultithreadEventLoopGroup.class);
    // 默认的 EventLoop 数量
    private static final int DEFAULT_EVENT_LOOP_THREADS;

    static {
        // 默认数量由传入的 JVM 参数决定，如果没有传入，那么为  CPU 核心数 * 2
        DEFAULT_EVENT_LOOP_THREADS = Math.max(1, SystemPropertyUtil.getInt(
                "io.netty.eventLoopThreads", Runtime.getRuntime().availableProcessors() * 2));

        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty.eventLoopThreads: {}", DEFAULT_EVENT_LOOP_THREADS);
        }
    }

    // 构造方法都是调用了父类 MultithreadEventExecutorGroup 的构造方法
    protected MultithreadEventLoopGroup(int nThreads, Executor executor, Object... args) {
        super(nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads, executor, args);
    }

    protected MultithreadEventLoopGroup(int nThreads, ThreadFactory threadFactory, Object... args) {
        super(nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads, threadFactory, args);
    }

    protected MultithreadEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                                     Object... args) {
        super(nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads, executor, chooserFactory, args);
    }

    @Override
    protected ThreadFactory newDefaultThreadFactory() {
        return new DefaultThreadFactory(getClass(), Thread.MAX_PRIORITY);
    }

    @Override
    public EventLoop next() {
        return (EventLoop) super.next();
    }

    // 不同类型的 EventLoopGroup 对应不同类型的 EventLoop，所以这里没有实现
    @Override
    protected abstract EventLoop newChild(Executor executor, Object... args) throws Exception;

    // 从父类继承的方法都是调用了父类的 next() 获取 EventLoop 以后将 Channel 注册到上面去，由此可见一个 Channel 注册到一个 EventLoop，一个 EventLoop 上绑定了多个 Channel
    @Override
    public ChannelFuture register(Channel channel) {
        return next().register(channel);
    }

    @Override
    public ChannelFuture register(ChannelPromise promise) {
        return next().register(promise);
    }

    @Deprecated
    @Override
    public ChannelFuture register(Channel channel, ChannelPromise promise) {
        return next().register(channel, promise);
    }
}
```

## DefaultEventLoopGroup

`DefaultEventLoopGroup` 是用于本地传输的 `EventLoopGroup`。

```java
public class DefaultEventLoopGroup extends MultithreadEventLoopGroup {

    public DefaultEventLoopGroup() {
        this(0); // 默认线程是 0，最终传到 MultithreadEventLoopGroup 的构造方法中，由用户传入的 JVM 参数或执行机器的 CPU 决定
    }

    public DefaultEventLoopGroup(int nThreads) {
        this(nThreads, (ThreadFactory) null);
    }

    public DefaultEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        super(nThreads, threadFactory);
    }

    public DefaultEventLoopGroup(int nThreads, Executor executor) {
        super(nThreads, executor);
    }

    // 默认的 EventLoopGroup 管理的 EventLoop 当然是 DefaultEventLoop
    @Override
    protected EventLoop newChild(Executor executor, Object... args) throws Exception {
        return new DefaultEventLoop(this, executor);
    }
}
```

## NioEventLoopGroup

`NioEventLoopGroup` 是基于 Java NIO，使用 Selector 和 Channel 的 `EventLoopGroup` 实现。

```java
public class NioEventLoopGroup extends MultithreadEventLoopGroup {

    public NioEventLoopGroup() {
        this(0);
    }

    public NioEventLoopGroup(int nThreads) {
        this(nThreads, (Executor) null);
    }

    public NioEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        this(nThreads, threadFactory, SelectorProvider.provider());
    }

    public NioEventLoopGroup(int nThreads, Executor executor) {
        this(nThreads, executor, SelectorProvider.provider());
    }

    public NioEventLoopGroup(
            int nThreads, ThreadFactory threadFactory, final SelectorProvider selectorProvider) {
        this(nThreads, threadFactory, selectorProvider, DefaultSelectStrategyFactory.INSTANCE);
    }

    // NIO 用到了 Selector，NioEventLoop 需要传入 SelectorProvider，这里传入的 Select 策略用来控制 Selector
    public NioEventLoopGroup(int nThreads, ThreadFactory threadFactory,
        final SelectorProvider selectorProvider, final SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, threadFactory, selectorProvider, selectStrategyFactory, RejectedExecutionHandlers.reject());
    }

    public NioEventLoopGroup(
            int nThreads, Executor executor, final SelectorProvider selectorProvider) {
        this(nThreads, executor, selectorProvider, DefaultSelectStrategyFactory.INSTANCE);
    }

    public NioEventLoopGroup(int nThreads, Executor executor, final SelectorProvider selectorProvider,
                             final SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, executor, selectorProvider, selectStrategyFactory, RejectedExecutionHandlers.reject());
    }

    public NioEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                             final SelectorProvider selectorProvider,
                             final SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, executor, chooserFactory, selectorProvider, selectStrategyFactory,
                RejectedExecutionHandlers.reject());
    }

    public NioEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                             final SelectorProvider selectorProvider,
                             final SelectStrategyFactory selectStrategyFactory,
                             final RejectedExecutionHandler rejectedExecutionHandler) {
        super(nThreads, executor, chooserFactory, selectorProvider, selectStrategyFactory, rejectedExecutionHandler);
    }

    // 设置 IO 线程和 非 IO 线程花费时间的比例
    public void setIoRatio(int ioRatio) {
        for (EventExecutor e: this) {
            ((NioEventLoop) e).setIoRatio(ioRatio);
        }
    }

    // 用新创建的 Selector 重置 EventLoop 的 selector，用来解决 Java NIO 框架中著名的 epoll CPU 占用 100% 的 bug
    public void rebuildSelectors() {
        for (EventExecutor e: this) {
            ((NioEventLoop) e).rebuildSelector();
        }
    }

    // NioEventLoopGroup 对应 NioEventLoop
    @Override
    protected EventLoop newChild(Executor executor, Object... args) throws Exception {
        return new NioEventLoop(this, executor, (SelectorProvider) args[0],
            ((SelectStrategyFactory) args[1]).newSelectStrategy(), (RejectedExecutionHandler) args[2]);
    }
}
```

`NioEventLoopGroup` 是为基于 Java NIO 的线程服务的，构造参数中传入了 `Selector` 相关的属性，创建 `NioEventLoop` 时传入这些参数，并且提供了设置 `NioEventLoop` 处理 IO Task 和非 IO Task 的时间比例，也提供了因为 NIO 框架出现的 epoll 导致 CPU 占用 100% 时的解决方案: `rebuildSelectors`，创建新的 `Selector`，具体实现后面再说。

## EpollEventLoopGroup

`EpollEventLoopGroup` 是专门在 Linux 系统上工作的 `EventLoopGroup`，它的实现和 `NioEventLoopGroup` 大致相同。

相同点在于构造方法结构相同，传入的参数在创建 `EventLoop` 时使用，但是不同的是，传入了一个 `maxEventsAtOnce` 参数限制了每个 `EpollEventLoop` 中等待处理的最大 epoll 事件数量，并且 `EpollEventLoopGroup` 没有设置 Java 自带 NIO 框架的 `Selector`，因为 `EpollEventLoop` 通过 JNI（Java Native Interface）调用自己实现的 Native 方法，直接调用 Linux Kernel 的 epoll 机制，相比于 Java NIO 更加高效，并且没有 Java NIO 中的 CPU 100% 的 bug。

代码很简单，这里不再放出来。

## ThreadPerChannelEventLoopGroup

顾名思义，为每个 `Channel` 都分配一个 `EventLoop`，但是一个 `EventLoop` 也只绑定这一个 `Channel`。

```java
public class ThreadPerChannelEventLoopGroup extends AbstractEventExecutorGroup implements EventLoopGroup {

    private final Object[] childArgs;
    private final int maxChannels; // 最大绑定 Channel 数量
    final Executor executor;
    // 正在使用的 EventLoop
    final Set<EventLoop> activeChildren =
            Collections.newSetFromMap(PlatformDependent.<EventLoop, Boolean>newConcurrentHashMap());
    // 之前创建的，并且已经 deregister 以后的 EventLoop，重复利用
    final Queue<EventLoop> idleChildren = new ConcurrentLinkedQueue<EventLoop>();
    private final ChannelException tooManyChannels;

    private volatile boolean shuttingDown;
    private final Promise<?> terminationFuture = new DefaultPromise<Void>(GlobalEventExecutor.INSTANCE);
    // EventLoop 关闭的 listener
    private final FutureListener<Object> childTerminationListener = new FutureListener<Object>() {
        @Override
        public void operationComplete(Future<Object> future) throws Exception {
            // Inefficient, but works.
            if (isTerminated()) {
                terminationFuture.trySuccess(null);
            }
        }
    };

    // 创建一个没有 EventLoop 数量限制的 EventLoopGroup
    protected ThreadPerChannelEventLoopGroup() {
        this(0);
    }

    // 传入 Channel 绑定的最大数量，如果超过这个数量则会抛出 ChannelException
    protected ThreadPerChannelEventLoopGroup(int maxChannels) {
        this(maxChannels, Executors.defaultThreadFactory());
    }

    protected ThreadPerChannelEventLoopGroup(int maxChannels, ThreadFactory threadFactory, Object... args) {
        this(maxChannels, new ThreadPerTaskExecutor(threadFactory), args);
    }

    protected ThreadPerChannelEventLoopGroup(int maxChannels, Executor executor, Object... args) {
        if (maxChannels < 0) {
            throw new IllegalArgumentException(String.format(
                    "maxChannels: %d (expected: >= 0)", maxChannels));
        }
        if (executor == null) {
            throw new NullPointerException("executor");
        }

        if (args == null) {
            childArgs = EmptyArrays.EMPTY_OBJECTS;
        } else {
            childArgs = args.clone();
        }

        this.maxChannels = maxChannels;
        this.executor = executor;
        // 初始化 tooManyChannels 异常
        tooManyChannels = ThrowableUtil.unknownStackTrace(
                new ChannelException("too many channels (max: " + maxChannels + ')'),
                ThreadPerChannelEventLoopGroup.class, "nextChild()");
    }

    // 默认 child EventLoop 是 ThreadPerChannelEventLoop，一个 Single Thread 线程池
    protected EventLoop newChild(@SuppressWarnings("UnusedParameters") Object... args) throws Exception {
        return new ThreadPerChannelEventLoop(this);
    }

    @Override
    public Iterator<EventExecutor> iterator() {
        return new ReadOnlyIterator<EventExecutor>(activeChildren.iterator());
    }

    // 绑定 Channel 时不需要选一个已有的 EventLoop 去绑定，而是创建新的，因此抛出异常
    @Override
    public EventLoop next() {
        throw new UnsupportedOperationException();
    }

    // 中间的代码逻辑简单，和 EventExecutorGroup 的逻辑类似，这里不展示

    // 注册一个新的 Channel，调用 nextChild 方法创建一个新的 EventLoop
    @Override
    public ChannelFuture register(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        try {
            EventLoop l = nextChild();
            return l.register(new DefaultChannelPromise(channel, l));
        } catch (Throwable t) {
            return new FailedChannelFuture(channel, GlobalEventExecutor.INSTANCE, t);
        }
    }

    @Override
    public ChannelFuture register(ChannelPromise promise) {
        try {
            return nextChild().register(promise);
        } catch (Throwable t) {
            promise.setFailure(t);
            return promise;
        }
    }

    @Deprecated
    @Override
    public ChannelFuture register(Channel channel, ChannelPromise promise) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        try {
            return nextChild().register(channel, promise);
        } catch (Throwable t) {
            promise.setFailure(t);
            return promise;
        }
    }

    // 创建一个新的 EventLoop，如果已经到达绑定的最大值，抛出异常
    private EventLoop nextChild() throws Exception {
        if (shuttingDown) {
            throw new RejectedExecutionException("shutting down");
        }

        EventLoop loop = idleChildren.poll(); // 从之前创建使用后又注销的 EventLoop 中取一个，循环利用
        if (loop == null) {
            if (maxChannels > 0 && activeChildren.size() >= maxChannels) {
                throw tooManyChannels;
            }
            loop = newChild(childArgs);
            loop.terminationFuture().addListener(childTerminationListener);
        }
        activeChildren.add(loop);
        return loop;
    }
}
```

## OioEventLoopGroup

`OioEventLoopGroup` 是传统阻塞 IO 的线程池，它继承了 `ThreadPerChannelEventLoopGroup`。

```java
public class OioEventLoopGroup extends ThreadPerChannelEventLoopGroup {

    // 没有限制地绑定 Channel
    public OioEventLoopGroup() {
        this(0);
    }

    public OioEventLoopGroup(int maxChannels) {
        this(maxChannels, Executors.defaultThreadFactory());
    }

    public OioEventLoopGroup(int maxChannels, Executor executor) {
        super(maxChannels, executor);
    }

    public OioEventLoopGroup(int maxChannels, ThreadFactory threadFactory) {
        super(maxChannels, threadFactory);
    }
}
```

实现很简单，只要构造方法，必要的实现都已经在 `ThreadPerChannelEventLoopGroup` 中实现了。