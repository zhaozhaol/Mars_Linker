package com.mars.linker.broker.netty.acl;

/**
 * 发布/订阅 ACL 提供者（可插拔）。
 */
public interface AclProvider {
    public boolean allowsSubscribe(String topicFilter);

    public boolean allowsPublish(String topic);
}
