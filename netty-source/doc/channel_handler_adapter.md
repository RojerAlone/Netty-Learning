# ChannelHandlerAdapter

Netty 提供了 `ChannelHandler` 的抽象类实现 `ChannelHandlerAdapter`，代码如下：

```Java
public abstract class ChannelHandlerAdapter implements ChannelHandler {

    // 记录 handler 是否已经被加入到 pipeline 中
    // 在 DefaultChannelPipeline 中使用，如果已经被加入到一个 pipeline 中并且没有 @Sharable 注解，那么会抛异常
    boolean added;

    /**
     * 如果子类用 @Sharable 注解标记的话，返回 true
     */
    public boolean isSharable() {
        Class<?> clazz = getClass();
        Map<Class<?>, Boolean> cache = InternalThreadLocalMap.get().handlerSharableCache(); // 将 sharable 信息缓存
        Boolean sharable = cache.get(clazz);
        if (sharable == null) {
            sharable = clazz.isAnnotationPresent(Sharable.class);
            cache.put(clazz, sharable);
        }
        return sharable;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    /**
     * 事件传递给下一个 handler
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }
}
```

`ChannelHandlerAdapter` 实现很简单，对 `@Sharable` 注解的验证，以及传递异常给 pipeline 中的下一个 handler，Netty 本身不关心 handler 被添加或者删除，因此在实现里默认什么也不做，由开发者的业务决定代码逻辑，开发者重写这两个方法即可。

## ChannelInboundHandlerAdapter

`ChannelInboundHandlerAdapter` 继承了 `ChannelHandlerAdapter` 类，并且实现了 `ChannelInboundHandler` 方法，所有的实现方法都是默认将事件往下传递，用户根据自己的业务逻辑重写相应方法即可，Netty 只是提供一个入站 Handler 的适配器。

## ChannelOutboundHandlerAdapter

`ChannelInboundHandlerAdapter` 继承了 `ChannelHandlerAdapter` 类，并且实现了 `ChannelInboundHandler` 方法，所有的实现方法都是默认将事件往下传递，用户根据自己的业务逻辑重写相应方法即可，Netty 只是提供一个入站 Handler 的适配器。