package com.mars.linker.broker.netty.acl;

/**
 * 发布/订阅 ACL 提供者（可插拔）。
 */
public interface AclProvider extends AutoCloseable {
    public boolean allowsSubscribe(String topicFilter);

    public boolean allowsPublish(String topic);

    @Override
    default void close() {
        // no-op by default
    }
}
