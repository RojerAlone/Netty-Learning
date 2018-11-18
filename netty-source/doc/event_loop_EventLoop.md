# EventLoop 及其相关接口的实现 之 EventLoop

`EventLoop` 接口有抽象类实现 `AbstractEventLoop`，但是这个抽象类没有实现，可能是因为要和其他的接口有抽象类对齐，`EventLoop` 的基础实现是 `SingleThreadEventLoop`，`NioEventLoop` 和 `EpollEventLoop` 都继承自它。

## SingleThreadEventLoop

`SingleThreadEventLoop` 继承了 `SingleThreadEventExecutor`，实现了 `EventLoop` 接口的 `parent` 方法，不过也是调用父类的。

register 方法是调用 `Unsafe` 类的，除此之外还有个 `tailTask` 的队列，这个队列并没有使用到，可能是为了以后的扩展预留的。

```java
public abstract class SingleThreadEventLoop extends SingleThreadEventExecutor implements EventLoop {

    protected static final int DEFAULT_MAX_PENDING_TASKS = Math.max(16,
            SystemPropertyUtil.getInt("io.netty.eventLoop.maxPendingTasks", Integer.MAX_VALUE));

    private final Queue<Runnable> tailTasks; // 额外的任务队列，暂时没有使用到，根据查来的资料，应该是扩展用的

    protected SingleThreadEventLoop(EventLoopGroup parent, ThreadFactory threadFactory,
                                    boolean addTaskWakesUp, int maxPendingTasks,
                                    RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, threadFactory, addTaskWakesUp, maxPendingTasks, rejectedExecutionHandler);
        tailTasks = newTaskQueue(maxPendingTasks);
    }

    protected SingleThreadEventLoop(EventLoopGroup parent, Executor executor,
                                    boolean addTaskWakesUp, int maxPendingTasks,
                                    RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, executor, addTaskWakesUp, maxPendingTasks, rejectedExecutionHandler);
        tailTasks = newTaskQueue(maxPendingTasks);
    }

    @Override
    public EventLoopGroup parent() {
        return (EventLoopGroup) super.parent();
    }

    @Override
    public EventLoop next() {
        return (EventLoop) super.next();
    }

    @Override
    public ChannelFuture register(Channel channel) {
        return register(new DefaultChannelPromise(channel, this));
    }

    // register 都是调用了 Unsafe 的 register 方法
    @Override
    public ChannelFuture register(final ChannelPromise promise) {
        ObjectUtil.checkNotNull(promise, "promise");
        promise.channel().unsafe().register(this, promise);
        return promise;
    }

    @Deprecated
    @Override
    public ChannelFuture register(final Channel channel, final ChannelPromise promise) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (promise == null) {
            throw new NullPointerException("promise");
        }

        channel.unsafe().register(this, promise);
        return promise;
    }

    // 添加 tailTask 以及删除
    @UnstableApi
    public final void executeAfterEventLoopIteration(Runnable task) {
        ObjectUtil.checkNotNull(task, "task");
        if (isShutdown()) {
            reject();
        }

        if (!tailTasks.offer(task)) {
            reject(task);
        }

        if (wakesUpForTask(task)) {
            wakeup(inEventLoop());
        }
    }

    @UnstableApi
    final boolean removeAfterEventLoopIterationTask(Runnable task) {
        return tailTasks.remove(ObjectUtil.checkNotNull(task, "task"));
    }

    // 提交任务时是否执行 wakeup(bool) 方法
    @Override
    protected boolean wakesUpForTask(Runnable task) {
        return !(task instanceof NonWakeupRunnable);
    }

    // 所有任务跑完之后，再执行 tailTask，由此可以看出 task 的优先级，先执行 pendingTask，最后执行 tailTask
    @Override
    protected void afterRunningAllTasks() {
        runAllTasksFrom(tailTasks);
    }

    @Override
    protected boolean hasTasks() {
        return super.hasTasks() || !tailTasks.isEmpty();
    }

    @Override
    public int pendingTasks() {
        return super.pendingTasks() + tailTasks.size();
    }

    interface NonWakeupRunnable extends Runnable { }
}
```

## DefaultEventLoop

`DefaultEventLoop` 是本地通信的 `EventLoop`，所以不存在 Channel。

它只是实现了 `run()` 方法，在方法的死循环中，从任务队列中取任务并执行，然后更新执行时间，并且检测是否线程池停止了，如果停止了就退出循环。

```java
protected void run() {
    for (;;) {
        Runnable task = takeTask();
        if (task != null) {
            task.run();
            updateLastExecutionTime();
        }

        if (confirmShutdown()) {
            break;
        }
    }
}
```

## ThreadPerChannelEventLoop

`ThreadPerChannelEventLoop` 是 OIO 的 EventLoop，相比于 `DefaultEventLoop` 不同的是，由于是需要远程通信的，因此有 `Channel`，在注册的 Future 中添加了监听，注册成功以后把 channel 赋值给自己的变量。

`run` 方法的逻辑和 `DefaultEventLoop` 相同，不过在检测到线程池关闭以后，要对 channel 做一下处理。

```java
 protected void run() {
    for (;;) {
        Runnable task = takeTask();
        if (task != null) {
            task.run();
            updateLastExecutionTime();
        }

        Channel ch = this.ch;
        if (isShuttingDown()) {
            if (ch != null) {
                ch.unsafe().close(ch.unsafe().voidPromise());
            }
            if (confirmShutdown()) {
                break;
            }
        } else {
            if (ch != null) {
                // Handle deregistration
                if (!ch.isRegistered()) {
                    runAllTasks();
                    deregister();
                }
            }
        }
    }
}
```

## NioEventLoop



## EpollEventLoop