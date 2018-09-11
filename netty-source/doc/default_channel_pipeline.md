# DefaultChannelPipeline

Netty 提供了全局唯一的 `ChannelPipeline` 默认实现 `DefaultChannelPipeline`。

`ChannelPipeline` 里边并不是直接放了 `ChannelHandler`，而是用 `ChannelHandlerContext` 包装起来，可以理解为 `ChannelHandlerContext` 是 `ChannelHandler` 和 `ChannelPipeline` 的纽带。

我认为这样的方式是充分将各种组件解耦，`Channel` 只负责数据和发生事件的传输（传输给 `ChannelPipeline`），`ChannelPipeline` 用于将事件在所有的 `ChannelHandler` 中传输，`ChannelHandler` 负责业务逻辑处理。

## ChannelHandlerContext 链表

Pipeline 中的 `ChannelHandlerContext` 是用双向链表的结构存储的，同时提供了两个特殊的 `ChannelHandlerContext`：`HeadContext` 和 `TailContext`。

```java
public class DefaultChannelPipeline implements ChannelPipeline {
    final AbstractChannelHandlerContext head;
    final AbstractChannelHandlerContext tail;
    
    // pipeline 和 channel 绑定关系
    protected DefaultChannelPipeline(Channel channel) {
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
        succeededFuture = new SucceededChannelFuture(channel, null);
        voidPromise =  new VoidChannelPromise(channel, true);
    
        tail = new TailContext(this);
        head = new HeadContext(this);
    
        head.next = tail;
        tail.prev = head;
    }
}
```

### HeadContext 和 TailContext

`HeadContext` 和 `TailContext` 都继承了抽象类 `AbstractChannelHandlerContext`（默认的 `DefaultChannelHandlerContext` 也继承了这个抽象类，大多数的 context 实现都已经在抽象类中实现了），同时 `HeadContext` 和 `TailContext` 也实现了 `ChannelInboundHandler` 或者 `ChannelOutboundHandler` 接口，它们自己就是 handler。

有一个概念需要明确，`ChannelPipeline` 中数据（或事件）流动的方向，在 `ChannelPipeline` 接口的文档注释上，已经说得很清楚关于 `ChannelPipeline` 的事件流向，这里重申一下，入站事件从 pipeline 链表流向是从头到尾，出站事件是从尾到头，也就是说，head 不仅是入站的第一个 handler，也是出站的最后一个 handler，这一点很重要，因此 `HeadContext` 不仅实现了 `ChannelInboundHandler`，也实现了 `ChannelOutboundHandler`。

## addLast 方法

对于 `ChannelPipeline`，开发者最常接触的就是 `addLast` 方法，来看下这个方法的详细实现。

```java
@Override
public final ChannelPipeline addLast(ChannelHandler... handlers) {
     return addLast(null, handlers);
}

@Override
public final ChannelPipeline addLast(EventExecutorGroup executor, ChannelHandler... handlers) {
    if (handlers == null) {
        throw new NullPointerException("handlers");
    }

    for (ChannelHandler h: handlers) {
        if (h == null) {
            break;
        }
        addLast(executor, null, h);
    }

    return this;
}

// 所有的 addLast 方法最后都是调用这个方法
// `addLast` 参数中带 handler 名字的方法，最后调用的也是这个方法
@Override
public final ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
    final AbstractChannelHandlerContext newCtx;
    synchronized (this) { // 加锁保证线程安全
        checkMultiplicity(handler); // 检查 handler 是否正确使用了 @Sharable 注解

        newCtx = newContext(group, filterName(name, handler), handler); // filterName 检查 handler 名字是否合法

        addLast0(newCtx); // 添加到双链表中

        // 如果 channel 还没注册到 eventloop，把 newCtx 加到 pipeline 中，并且添加一个在 channel 注册之后调用的任务
        if (!registered) {
            newCtx.setAddPending(); // 设置状态
            callHandlerCallbackLater(newCtx, true); // 添加注册之后要调用的任务 PendingHandlerAddedTask
            return this;
        }

        EventExecutor executor = newCtx.executor();
        if (!executor.inEventLoop()) { // 如果当前线程不属于 newCtx 的 eventLoop
            newCtx.setAddPending();
            executor.execute(new Runnable() { // 提交 handlerAdd 任务到线程池
                @Override
                public void run() {
                    callHandlerAdded0(newCtx);
                }
            });
            return this;
        }
    }
    callHandlerAdded0(newCtx); // 触发 handlerAdd 事件
    return this;
}

// 将新的 ChannelHandlerContext 添加到双链表中
private void addLast0(AbstractChannelHandlerContext newCtx) {
    AbstractChannelHandlerContext prev = tail.prev;
    newCtx.prev = prev;
    newCtx.next = tail;
    prev.next = newCtx;
    tail.prev = newCtx;
}
```

## filterName

filterName 用于对 handler 的名字做合法性检查。

```java
private String filterName(String name, ChannelHandler handler) {
    if (name == null) {
        return generateName(handler); // 自动生成名字
    }
    checkDuplicateName(name); // 如果用户传入了名字，检查是否有重名的 handler，从头到尾遍历 pipeline 中的 context 链表
    return name;
}

// 自动生成名字
private String generateName(ChannelHandler handler) {
    Map<Class<?>, String> cache = nameCaches.get();
    Class<?> handlerType = handler.getClass();
    String name = cache.get(handlerType);
    if (name == null) {
        name = generateName0(handlerType); // 自动生成的名字为 {类名}#0
        cache.put(handlerType, name);
    }

    // 用户可能传入多个类型相同的 handler，因此要做处理，对其他类型相同的 handler，自动生成的名字不再加入缓存
    if (context0(name) != null) {
        String baseName = name.substring(0, name.length() - 1); // Strip the trailing '0'.
        for (int i = 1;; i ++) { // 找到可以用的序号 {类名}#{id}
            String newName = baseName + i;
            if (context0(newName) == null) {
                name = newName;
                break;
            }
        }
    }
    return name;
}
```

在这里用到了 cache，这个缓存是对 handler 和它所对应的名字的 ThreadLocal 缓存，一个 channel 对应一个 EventLoop，使用了 ThreadLocal 缓存。