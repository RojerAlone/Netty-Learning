# EventLoop 及其相关接口的实现 之 EventExecutor 接口实现

`EventExecutor` 继承了 `EventExecutorGroup`，这意味着它也是一个线程池，同时 `EventLoop` 接口继承了它，而 `EventLoopGroup` 继承了 `EventExecutorGroup`，可见 `EventExecutor` 是 `EventLoop` 和 `EventLoopGroup` 的不同点所在。

## AbstractEventExecutor

`AbstractEventExecutor` 是 `EventExecutor` 的基础抽象实现类，这个类继承了 JDK 自带的线程池抽象类 `AbstractExecutorService`，并且将 `AbstractExecutorService` 的提交任务相关方法的返回值转化为 Netty 自己的 `Future` 对象。

```java
public abstract class AbstractEventExecutor extends AbstractExecutorService implements EventExecutor {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractEventExecutor.class);

    static final long DEFAULT_SHUTDOWN_QUIET_PERIOD = 2; // 默认的关闭线程池时候的静默时间
    static final long DEFAULT_SHUTDOWN_TIMEOUT = 15; // 关闭线程池的超时时间

    private final EventExecutorGroup parent; // parent，可能为空
    private final Collection<EventExecutor> selfCollection = Collections.<EventExecutor>singleton(this); // 可见 EventExecutor 是单线程的 

    protected AbstractEventExecutor() {
        this(null);
    }

    protected AbstractEventExecutor(EventExecutorGroup parent) {
        this.parent = parent;
    }

    @Override
    public EventExecutorGroup parent() {
        return parent;
    }

    // 继承自父类的方法，next 返回自己
    @Override
    public EventExecutor next() {
        return this;
    }

    // 调用子类的 inEventLoop 方法判断
    @Override
    public boolean inEventLoop() {
        return inEventLoop(Thread.currentThread());
    }

    @Override
    public Iterator<EventExecutor> iterator() {
        return selfCollection.iterator();
    }

    // 调用子类的实现
    @Override
    public Future<?> shutdownGracefully() {
        return shutdownGracefully(DEFAULT_SHUTDOWN_QUIET_PERIOD, DEFAULT_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
    }

    // cast to self Future
    @Override
    public Future<?> submit(Runnable task) {
        return (Future<?>) super.submit(task);
    }

    // 延迟执行的相关方法都抛出 UnsupportedOperationException，AbstractScheduledEventExecutor 子类重写
    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay,
                                       TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    // 直接执行提交的方法，抛出异常时打日志
    protected static void safeExecute(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            logger.warn("A task raised an exception. Task: {}", task, t);
        }
    }
}
```

实现简单，部分逻辑相同的方法略去了。

## AbstractScheduledEventExecutor

`AbstractScheduledEventExecutor` 继承了 `AbstractEventExecutor`，对其没有实现的 `schedule` 相关方法进行实现。

```java
public abstract class AbstractScheduledEventExecutor extends AbstractEventExecutor {

    Queue<ScheduledFutureTask<?>> scheduledTaskQueue; // 待执行任务的队列

    protected AbstractScheduledEventExecutor() {
    }

    protected AbstractScheduledEventExecutor(EventExecutorGroup parent) {
        super(parent);
    }

    // 当前时间距离启动时间的纳秒数
    protected static long nanoTime() {
        return ScheduledFutureTask.nanoTime();
    }


    // 只能当 inEventLoop 是 true 的时候调用，取消所有的延时任务
    protected void cancelScheduledTasks() {
        assert inEventLoop();
        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        if (isNullOrEmpty(scheduledTaskQueue)) {
            return;
        }

        final ScheduledFutureTask<?>[] scheduledTasks =
                scheduledTaskQueue.toArray(new ScheduledFutureTask<?>[scheduledTaskQueue.size()]);

        for (ScheduledFutureTask<?> task: scheduledTasks) {
            task.cancelWithoutRemove(false);
        }

        scheduledTaskQueue.clear();
    }

    protected final Runnable pollScheduledTask() {
        return pollScheduledTask(nanoTime());
    }

    // 返回一个给定时间内即将被执行的任务
    protected final Runnable pollScheduledTask(long nanoTime) {
        assert inEventLoop();

        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        // 返回的是第一个任务
        ScheduledFutureTask<?> scheduledTask = scheduledTaskQueue == null ? null : scheduledTaskQueue.peek();
        if (scheduledTask == null) {
            return null;
        }

        if (scheduledTask.deadlineNanos() <= nanoTime) {
            scheduledTaskQueue.remove();
            return scheduledTask;
        }
        return null;
    }

    // 距离下一个任务执行的时间
    protected final long nextScheduledTaskNano() {
        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        ScheduledFutureTask<?> scheduledTask = scheduledTaskQueue == null ? null : scheduledTaskQueue.peek();
        if (scheduledTask == null) {
            return -1;
        }
        return Math.max(0, scheduledTask.deadlineNanos() - nanoTime());
    }

    final ScheduledFutureTask<?> peekScheduledTask() {
        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        if (scheduledTaskQueue == null) {
            return null;
        }
        return scheduledTaskQueue.peek();
    }

    // 是否有一个任务准备处理
    protected final boolean hasScheduledTasks() {
        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        ScheduledFutureTask<?> scheduledTask = scheduledTaskQueue == null ? null : scheduledTaskQueue.peek();
        return scheduledTask != null && scheduledTask.deadlineNanos() <= nanoTime();
    }

    @Override
    public  ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        ObjectUtil.checkNotNull(command, "command");
        ObjectUtil.checkNotNull(unit, "unit");
        if (delay < 0) {
            throw new IllegalArgumentException(
                    String.format("delay: %d (expected: >= 0)", delay));
        }
        return schedule(new ScheduledFutureTask<Void>(
                this, command, null, ScheduledFutureTask.deadlineNanos(unit.toNanos(delay))));
    }

    // 其他 schedule 方法最后调用的都是这个方法，将参数封装到 ScheduledFutureTask 中提交
    // 具体实现很简单，如果是自己应该处理的任务，提交到等待队列中，不是的话调用父类的方法提交
    <V> ScheduledFuture<V> schedule(final ScheduledFutureTask<V> task) {
        if (inEventLoop()) {
            scheduledTaskQueue().add(task);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    scheduledTaskQueue().add(task);
                }
            });
        }

        return task;
    }

    // 移除一个任务
    final void removeScheduled(final ScheduledFutureTask<?> task) {
        if (inEventLoop()) {
            scheduledTaskQueue().remove(task);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    removeScheduled(task);
                }
            });
        }
    }
}
```

继承的 `schedule` 方法都是将参数封装到自己的 `ScheduledFutureTask` 中，如果是自己应该执行的任务就提交到等待队列，不是自己的就执行 `execute` 方法让其他线程处理。

## SingleThreadEventExecutor
