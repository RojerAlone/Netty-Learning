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

## HeadContext 和 TailContext

`HeadContext` 和 `TailContext` 都继承了抽象类 `AbstractChannelHandlerContext`（默认的 `DefaultChannelHandlerContext` 也继承了这个抽象类，大多数的 context 实现都已经在抽象类中实现了），同时 `HeadContext` 和 `TailContext` 也实现了 `ChannelInboundHandler` 或者 `ChannelOutboundHandler` 接口，它们自己就是 handler。

有一个概念需要明确，`ChannelPipeline` 中数据（或事件）流动的方向，在 `ChannelPipeline` 接口的文档注释上，已经说得很清楚关于 `ChannelPipeline` 的事件流向，这里重申一下，入站事件从 pipeline 链表流向是从头到尾，出站事件是从尾到头，也就是说，head 不仅是入站的第一个 handler，也是出站的最后一个 handler，这一点很重要，因此 `HeadContext` 不仅实现了 `ChannelInboundHandler`，也实现了 `ChannelOutboundHandler`。

### HeadContext