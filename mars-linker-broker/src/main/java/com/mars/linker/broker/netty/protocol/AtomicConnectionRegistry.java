package com.mars.linker.broker.netty.protocol;

import com.mars.linker.broker.netty.CloseReason;
import com.mars.linker.broker.netty.ClientSessionContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 原子化连接注册表，封装 clientId→ChannelId 和 ChannelId→ChannelHandlerContext 两个映射。
 * <p>
 * 核心设计决策：
 * - 使用 {@link ConcurrentHashMap#compute} 实现原子复合操作，避免同 ClientID 双活
 * - 踢旧连接通过旧 Channel 的 EventLoop 异步执行 close，避免跨 EventLoop 直接操作
 * - atomicRemove 使用 {@link ConcurrentHashMap#remove(Object, Object)} 防止误删新连接映射
 * </p>
 * <p>
 * 线程安全策略：
 * - 所有公开方法基于 ConcurrentHashMap 的原子操作，无需外部同步
 * - Channel.close() 通过 EventLoop 异步执行，不在此类中同步等待
 * </p>
 */
public class AtomicConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(AtomicConnectionRegistry.class);

    private final ConcurrentHashMap<ChannelId, ChannelHandlerContext> channels = new ConcurrentHashMap<>(16384);
    private final ConcurrentHashMap<String, ChannelId> clientToChannel = new ConcurrentHashMap<>(16384);

    /**
     * 原子踢旧连接并注册新连接。
     * <p>
     * 操作语义：查找 clientId 对应的旧 ChannelId → 如果旧连接活跃则异步关闭 → 注册新 ChannelId。
     * 整个操作在 compute 内完成，保证同一时刻同一 clientId 最多一个活跃连接。
     * </p>
     *
     * @param clientId 客户端标识
     * @param newCtx   新连接的 ChannelHandlerContext
     * @return 被踢掉的旧 ChannelId（可能为 null，表示无旧连接）
     */
    public ChannelId atomicKickAndRegister(String clientId, ChannelHandlerContext newCtx) {
        ChannelId[] kickedOld = new ChannelId[1];
        clientToChannel.compute(clientId, (key, oldChannelId) -> {
            ChannelId newChannelId = newCtx.channel().id();
            if (oldChannelId != null && !oldChannelId.equals(newChannelId)) {
                kickedOld[0] = oldChannelId;
                ChannelHandlerContext oldCtx = channels.get(oldChannelId);
                if (oldCtx != null && oldCtx.channel().isActive()) {
                    ClientSessionContext.of(oldCtx).closeReason(CloseReason.KICKED_BY_NEW_CONNECTION);
                    if (oldCtx.channel().eventLoop().inEventLoop()) {
                        oldCtx.close();
                    } else {
                        oldCtx.channel().eventLoop().execute(oldCtx::close);
                    }
                    log.info("踢旧连接: clientId={}, oldChannel={}, newChannel={}",
                            clientId, oldChannelId, newChannelId);
                }
                channels.remove(oldChannelId);
            }
            channels.put(newChannelId, newCtx);
            return newChannelId;
        });
        return kickedOld[0];
    }

    /**
     * 原子移除连接映射（仅在 ChannelId 匹配时移除，防止误删新连接）。
     *
     * @param clientId    客户端标识
     * @param channelId   期望被移除的 ChannelId
     * @return true 表示成功移除，false 表示 ChannelId 不匹配（已被新连接替换）
     */
    public boolean atomicRemove(String clientId, ChannelId channelId) {
        boolean removed = clientToChannel.remove(clientId, channelId);
        if (removed) {
            channels.remove(channelId);
        }
        return removed;
    }

    /**
     * 查询 clientId 对应的 ChannelHandlerContext。
     *
     * @param clientId 客户端标识
     * @return 对应的 ChannelHandlerContext，不存在则返回 null
     */
    public ChannelHandlerContext get(String clientId) {
        ChannelId channelId = clientToChannel.get(clientId);
        if (channelId == null) {
            return null;
        }
        return channels.get(channelId);
    }

    /**
     * 查询 ChannelId 对应的 ChannelHandlerContext。
     */
    public ChannelHandlerContext getByChannelId(ChannelId channelId) {
        return channels.get(channelId);
    }

    /**
     * 查询 clientId 对应的 ChannelId。
     */
    public ChannelId getChannelId(String clientId) {
        return clientToChannel.get(clientId);
    }

    /**
     * 获取当前活跃连接数。
     */
    public int activeCount() {
        return clientToChannel.size();
    }

    /**
     * 获取所有已注册的 ChannelHandlerContext。
     */
    public Collection<ChannelHandlerContext> allChannels() {
        return Collections.unmodifiableCollection(channels.values());
    }

    /**
     * 获取所有已注册的 clientId。
     */
    public Collection<String> allClientIds() {
        return Collections.unmodifiableCollection(clientToChannel.keySet());
    }

    /**
     * 判断 clientId 是否已注册。
     */
    public boolean containsClient(String clientId) {
        return clientToChannel.containsKey(clientId);
    }
}
