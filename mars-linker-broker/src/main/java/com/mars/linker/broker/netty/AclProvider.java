package com.mars.linker.broker.netty;

/**
 * 发布/订阅 ACL 提供者（可插拔）。
 */
interface AclProvider {
    boolean allowsSubscribe(String topicFilter);

    boolean allowsPublish(String topic);
}
