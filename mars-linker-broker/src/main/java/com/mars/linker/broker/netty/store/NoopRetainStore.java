package com.mars.linker.broker.netty.store;

import java.util.Collections;
import java.util.List;

/**
 * 关闭持久化时使用的空实现：retain 仅当前进程内存逻辑，不做持久化。
 */
public final class NoopRetainStore implements RetainStore {
    @Override
    public void put(String topic, byte[] payload, int qos) {
        // no-op
    }

    @Override
    public void remove(String topic) {
        // no-op
    }

    @Override
    public List<RetainedMessage> list() {
        return Collections.emptyList();
    }
}
