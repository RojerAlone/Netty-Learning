# EventLoop 及其相关接口的实现 之 EventExecutorGroup

`EventLoop` 和 `EventLoopGroup` 都继承自 `EventExecutorGroup`，这是 Netty 中线程池的基本工具，先来看它的实现源码。

`EventExecutorGroup` 有 3 个实现，分别是抽象类 `AbstractEventExecutorGroup` 和 `MultithreadEventExecutorGroup` 以及默认实现类 `DefaultEventExecutorGroup`，依次来看源码。
 
## AbstractEventExecutorGroup
 
`AbstractEventExecutorGroup` 只实现了继承自 JDK 自带线程池的方法，这里就不放源码了，因为它的源码太简单了，所有的方法都是通过 `next()` 方法获取到一个 `EventExecutor` 之后，把参数传递给 `EventExecutor` 对应的方法去执行。
 
## MultithreadEventExecutorGroup

`EventExecutorGroup` 是一组线程，因此不存在单线程的线程池，`MultithreadEventExecutorGroup` 也是一个抽象类，继承了 `AbstractEventExecutorGroup`，在这个抽象类中实现了 `EventExecutorGroup` 接口自己的方法。

### 类参数和构造方法
```java
public abstract class MultithreadEventExecutorGroup extends AbstractEventExecutorGroup {

    private final EventExecutor[] children; // 管理的 event executor 是用数组存起来的
    private final Set<EventExecutor> readonlyChildren; // 为了防止使用 Iterator 时候 EventExecutor 被修改，将 children 拷贝了一份只读的副本用来供迭代器使用
    private final AtomicInteger terminatedChildren = new AtomicInteger(); // 关闭的时候记录已经关闭的 EventExecutor 的数量，使用 terminationFuture 方法获得的 Future 在关闭以后将会得到通知
    private final Promise<?> terminationFuture = new DefaultPromise(GlobalEventExecutor.INSTANCE); // 关闭线程池的 Future
    private final EventExecutorChooserFactory.EventExecutorChooser chooser; // 调用 next() 方法时选取 EventExecutor 的选择器

    // args 是创建 子EventExecutor 时候使用的
    protected MultithreadEventExecutorGroup(int nThreads, ThreadFactory threadFactory, Object... args) {
        this(nThreads, threadFactory == null ? null : new ThreadPerTaskExecutor(threadFactory), args);
    }

    // 需要传入一个 Executor 实现类，默认的 ThreadPerTaskExecutor 是提交任务立马就执行，没有池化的 executor
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor, Object... args) {
        // 传入默认的选择器，后面再看源码
        this(nThreads, executor, DefaultEventExecutorChooserFactory.INSTANCE, args);
    }

    protected MultithreadEventExecutorGroup(int nThreads, Executor executor,
                                            EventExecutorChooserFactory chooserFactory, Object... args) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException(String.format("nThreads: %d (expected: > 0)", nThreads));
        }

        if (executor == null) {
            executor = new ThreadPerTaskExecutor(newDefaultThreadFactory());
        }

        children = new EventExecutor[nThreads];

        // 初始化 EventExecutor 数组
        for (int i = 0; i < nThreads; i ++) {
            boolean success = false;
            try {
                children[i] = newChild(executor, args);
                success = true;
            } catch (Exception e) {
                // TODO: Think about if this is a good exception type
                throw new IllegalStateException("failed to create a child event loop", e);
            } finally {
                // 如果初始化失败，关闭已经初始化的 EventExecutor
                if (!success) {
                    for (int j = 0; j < i; j ++) {
                        children[j].shutdownGracefully();
                    }
                    // 确认完全关闭所有的 EventExecutor
                    for (int j = 0; j < i; j ++) {
                        EventExecutor e = children[j];
                        try {
                            while (!e.isTerminated()) {
                                e.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
                            }
                        } catch (InterruptedException interrupted) {
                            // Let the caller handle the interruption.
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }

        chooser = chooserFactory.newChooser(children); // 从 ChooserFactory 获取具体的 chooser

        // 线程池关闭的 listener，只有当所有的 EventExecutor 都关闭以后才会设置为 true
        final FutureListener<Object> terminationListener = new FutureListener<Object>() {
            @Override
            public void operationComplete(Future<Object> future) throws Exception {
                if (terminatedChildren.incrementAndGet() == children.length) {
                    terminationFuture.setSuccess(null);
                }
            }
        };
        // 将 terminationListener 添加到所有 EventExecutor 的 terminationFuture 中
        for (EventExecutor e: children) {
            e.terminationFuture().addListener(terminationListener);
        }
        // 拷贝一份不能修改的 EventExecutor 备份
        Set<EventExecutor> childrenSet = new LinkedHashSet<EventExecutor>(children.length);
        Collections.addAll(childrenSet, children);
        readonlyChildren = Collections.unmodifiableSet(childrenSet);
    }
    
    protected ThreadFactory newDefaultThreadFactory() {
        return new DefaultThreadFactory(getClass());
    }
    
    // 创建一个子 EventExecutor，由于不确定子 EventExecutor 是 EventLoop 还是 EventLoopGroup，因此没有实现
    protected abstract EventExecutor newChild(Executor executor, Object... args) throws Exception;
}
```

从代码中可以看出，`EventExecutorGroup` 管理的 `EventExecutor` 是用数组存放的，初始化的时候会创建 `EventExecutor` 以及添加 termination listener，`next()` 方法从 `EventExecutor`s 中选取一个，选取策略则是 `EventExecutorChooser` 决定的。

### 其他方法实现
    
```java
// next 由 chooser 决定
@Override
public EventExecutor next() {
    return chooser.next();
}

@Override
public Iterator<EventExecutor> iterator() {
    return readonlyChildren.iterator();
}

public final int executorCount() {
    return children.length;
}

@Override
public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
    // 向所有的 EventExecutor 发送 shutdownGracefully 通知
    for (EventExecutor l: children) {
        l.shutdownGracefully(quietPeriod, timeout, unit);
    }
    return terminationFuture();
}

@Override
public Future<?> terminationFuture() {
    return terminationFuture;
}

@Override
@Deprecated
public void shutdown() {
    for (EventExecutor l: children) {
        l.shutdown();
    }
}

@Override
public boolean isShuttingDown() {
    for (EventExecutor l: children) {
        if (!l.isShuttingDown()) {
            return false;
        }
    }
    return true;
}

@Override
public boolean isShutdown() {
    for (EventExecutor l: children) {
        if (!l.isShutdown()) {
            return false;
        }
    }
    return true;
}

@Override
public boolean isTerminated() {
    for (EventExecutor l: children) {
        if (!l.isTerminated()) {
            return false;
        }
    }
    return true;
}

@Override
public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException {
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    loop: for (EventExecutor l: children) {
        for (;;) {
            long timeLeft = deadline - System.nanoTime();
            if (timeLeft <= 0) {
                break loop;
            }
            if (l.awaitTermination(timeLeft, TimeUnit.NANOSECONDS)) {
                break;
            }
        }
    }
    return isTerminated();
}
```

其他部分的方法实现都很简单，不再赘述。

### EventExecutorChooser

`EventExecutorGroup` 的 `next` 方法选择 `EventExecutor` 的策略是由 `EventExecutorChooser` 提供的，`EventExecutorChooser` 是 `DefaultEventExecutorChooserFactory` 的子类，它的实现很有意思。

```java
public final class DefaultEventExecutorChooserFactory implements EventExecutorChooserFactory {

    // 单例
    public static final DefaultEventExecutorChooserFactory INSTANCE = new DefaultEventExecutorChooserFactory();

    private DefaultEventExecutorChooserFactory() { }

    @SuppressWarnings("unchecked")
    @Override
    public EventExecutorChooser newChooser(EventExecutor[] executors) {
        // 根据传入的 executors 的奇偶来决定选择策略，isPowerOfTwo 判断是否是 2 的次方
        if (isPowerOfTwo(executors.length)) {
            return new PowerOfTowEventExecutorChooser(executors);
        } else {
            return new GenericEventExecutorChooser(executors);
        }
    }

    // 判断一个整数是否是 2 的次方，实现非常 cool
    private static boolean isPowerOfTwo(int val) {
        return (val & -val) == val;
    }

    // 当 executors 是 2 的次方倍时候的选择策略
    private static final class PowerOfTowEventExecutorChooser implements EventExecutorChooser {
        private final AtomicInteger idx = new AtomicInteger();
        private final EventExecutor[] executors;

        PowerOfTowEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        @Override
        public EventExecutor next() {
            // 这就是为什么根据是否是 2 的次方来指定选择策略，当是 2 的次方的时候，可以使用 与 操作来提高性能
            return executors[idx.getAndIncrement() & executors.length - 1];
        }
    }

    // 通用选择策略
    private static final class GenericEventExecutorChooser implements EventExecutorChooser {
        private final AtomicInteger idx = new AtomicInteger();
        private final EventExecutor[] executors;

        GenericEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        @Override
        public EventExecutor next() {
            return executors[Math.abs(idx.getAndIncrement() % executors.length)];
        }
    }
}
```

chooser 的实现还是很精巧的，但是代码还可以精简，两个 Chooser 代码都几乎相同，只有选择时候的策略不同，可以抽取出一个 `int next(AtomicInteger idx, int size)` 方法来选择 next，不同的策略有不同的实现就可以了。

## DefaultEventExecutorGroup

```java
public class DefaultEventExecutorGroup extends MultithreadEventExecutorGroup {
    
    public DefaultEventExecutorGroup(int nThreads) {
        this(nThreads, null);
    }

    public DefaultEventExecutorGroup(int nThreads, ThreadFactory threadFactory) {
        this(nThreads, threadFactory, SingleThreadEventExecutor.DEFAULT_MAX_PENDING_EXECUTOR_TASKS,
                RejectedExecutionHandlers.reject());
    }

    // maxPendingTasks 是新任务被拒绝之前还可以提交的任务最大值，rejectedHandler 就是等待队列满了以后的拒绝策略
    // 这两个参数其实就是 JDK 自带线程池中的 maximumPoolSize 以及 RejectedExecutionHandler，EventExecutorGroup 并没有使用而是在创建 EventExecutor 的时候传给了它
    public DefaultEventExecutorGroup(int nThreads, ThreadFactory threadFactory, int maxPendingTasks,
                                     RejectedExecutionHandler rejectedHandler) {
        super(nThreads, threadFactory, maxPendingTasks, rejectedHandler);
    }

    @Override
    protected EventExecutor newChild(Executor executor, Object... args) throws Exception {
        return new DefaultEventExecutor(this, executor, (Integer) args[0], (RejectedExecutionHandler) args[1]);
    }
}
```