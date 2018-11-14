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

`SingleThreadEventExecutor` 是单线程的线程池，它是个抽象类，继承了 `AbstarctScheduledEventExecutor`，实现了 `OrderedEventExecutor` 接口。

### 类属性和初始化
```java
// 等待执行的任务上限，最小为 16，最大为 Integer.MAX_VALUE
static final int DEFAULT_MAX_PENDING_EXECUTOR_TASKS = Math.max(16,
          SystemPropertyUtil.getInt("io.netty.eventexecutor.maxPendingTasks", Integer.MAX_VALUE));

// 线程池状态
private static final int ST_NOT_STARTED = 1; // 还没有启动，存在延迟启动的机制，直到有任务提交以后才会开始执行任务
private static final int ST_STARTED = 2;
private static final int ST_SHUTTING_DOWN = 3; // 正在关闭
private static final int ST_SHUTDOWN = 4;
private static final int ST_TERMINATED = 5;

// 
private static final Runnable WAKEUP_TASK = new Runnable() {
    @Override
    public void run() {
        // Do nothing.
    }
};
private static final Runnable NOOP_TASK = new Runnable() {
    @Override
    public void run() {
        // Do nothing.
    }
};

private static final AtomicIntegerFieldUpdater<SingleThreadEventExecutor> STATE_UPDATER;
private static final AtomicReferenceFieldUpdater<SingleThreadEventExecutor, ThreadProperties> PROPERTIES_UPDATER;
// 初始化 UPDATER
static {
    AtomicIntegerFieldUpdater<SingleThreadEventExecutor> updater =
            PlatformDependent.newAtomicIntegerFieldUpdater(SingleThreadEventExecutor.class, "state");
    if (updater == null) {
        updater = AtomicIntegerFieldUpdater.newUpdater(SingleThreadEventExecutor.class, "state");
    }
    STATE_UPDATER = updater;

    AtomicReferenceFieldUpdater<SingleThreadEventExecutor, ThreadProperties> propertiesUpdater =
            PlatformDependent.newAtomicReferenceFieldUpdater(SingleThreadEventExecutor.class, "threadProperties");
    if (propertiesUpdater == null) {
        propertiesUpdater = AtomicReferenceFieldUpdater.newUpdater(SingleThreadEventExecutor.class,
                                                               ThreadProperties.class, "threadProperties");
    }
    PROPERTIES_UPDATER = propertiesUpdater;
}
// 等待队列
private final Queue<Runnable> taskQueue;
// 执行任务的线程
private volatile Thread thread;
@SuppressWarnings("unused")
private volatile ThreadProperties threadProperties;
private final Executor executor;
private volatile boolean interrupted;

private final Semaphore threadLock = new Semaphore(0);
private final Set<Runnable> shutdownHooks = new LinkedHashSet<Runnable>();
private final boolean addTaskWakesUp; // 添加任务时是否唤醒线程池
private final int maxPendingTasks;
private final RejectedExecutionHandler rejectedExecutionHandler;

private long lastExecutionTime;

@SuppressWarnings({ "FieldMayBeFinal", "unused" })
private volatile int state = ST_NOT_STARTED;

private volatile long gracefulShutdownQuietPeriod;
private volatile long gracefulShutdownTimeout;
private long gracefulShutdownStartTime;

protected SingleThreadEventExecutor(EventExecutorGroup parent, Executor executor,
                                    boolean addTaskWakesUp, int maxPendingTasks,
                                    RejectedExecutionHandler rejectedHandler) {
    super(parent);
    this.addTaskWakesUp = addTaskWakesUp; // 是否在 addTask 时唤醒执行线程
    this.maxPendingTasks = Math.max(16, maxPendingTasks);
    this.executor = ObjectUtil.checkNotNull(executor, "executor");
    taskQueue = newTaskQueue(this.maxPendingTasks);
    rejectedExecutionHandler = ObjectUtil.checkNotNull(rejectedHandler, "rejectedHandler");
}
```

### 任务提交

线程池的任务提交方法当然为 `execute` 了，来看下这个方法：

```java
@Override
public void execute(Runnable task) {
    if (task == null) {
        throw new NullPointerException("task");
    }

    boolean inEventLoop = inEventLoop(); // 提交线程是否属于当前线程池
    if (inEventLoop) { // 线程池已启动，提交到等待队列
        addTask(task);
    } else { // 线程池未启动，先启动线程池（创建启动线程并启动），再提交任务到等待队列
        startThread(); // 启动线程池
        addTask(task);
        if (isShutdown() && removeTask(task)) { // 如果线程池已经关闭，删除任务
            reject();
        }
    }

    if (!addTaskWakesUp && wakesUpForTask(task)) { // 如果需要唤醒线程池，添加一个 WAKE_UP_TASK 到队列中
        wakeup(inEventLoop);
    }
}

@Override
public boolean inEventLoop(Thread thread) {
    return thread == this.thread; // 判断传入的线程是否和类变量是同一个线程
}
```

从源码中我们可以看出，提交任务的时候会进行唤醒线程池的操作。

### 唤醒线程池

```java
private void startThread() {
    if (STATE_UPDATER.get(this) == ST_NOT_STARTED) { // 启动前先检测状态
        if (STATE_UPDATER.compareAndSet(this, ST_NOT_STARTED, ST_STARTED)) { // 设置状态成功后启动
            doStartThread();
        }
    }
}

private void doStartThread() {
    assert thread == null;
    // 直接交给 ExecutorGroup 传进来的 executor 去执行新的线程
    executor.execute(new Runnable() {
        @Override
        public void run() {
            thread = Thread.currentThread();
            if (interrupted) {
                thread.interrupt();
            }

            boolean success = false;
            updateLastExecutionTime();
            try {
                SingleThreadEventExecutor.this.run(); // 子类的 run 方法执行起来
                success = true;
            } catch (Throwable t) {
                logger.warn("Unexpected exception from an event executor: ", t);
            } finally {
                for (;;) {
                    int oldState = STATE_UPDATER.get(SingleThreadEventExecutor.this);
                    if (oldState >= ST_SHUTTING_DOWN || STATE_UPDATER.compareAndSet(
                            SingleThreadEventExecutor.this, oldState, ST_SHUTTING_DOWN)) {
                        break;
                    }
                }

                // Check if confirmShutdown() was called at the end of the loop.
                if (success && gracefulShutdownStartTime == 0) {
                    logger.error("Buggy " + EventExecutor.class.getSimpleName() + " implementation; " +
                            SingleThreadEventExecutor.class.getSimpleName() + ".confirmShutdown() must be called " +
                            "before run() implementation terminates.");
                }

                try {
                    // Run all remaining tasks and shutdown hooks.
                    for (;;) {
                        if (confirmShutdown()) {
                            break;
                        }
                    }
                } finally {
                    try {
                        cleanup();
                    } finally {
                        STATE_UPDATER.set(SingleThreadEventExecutor.this, ST_TERMINATED);
                        threadLock.release();
                        if (!taskQueue.isEmpty()) {
                            logger.warn(
                                    "An event executor terminated with " +
                                            "non-empty task queue (" + taskQueue.size() + ')');
                        }

                        terminationFuture.setSuccess(null);
                    }
                }
            }
        }
    });
}
```