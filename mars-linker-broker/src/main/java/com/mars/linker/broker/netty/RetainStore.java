package com.mars.linker.broker.netty;

import java.util.List;

/**
 * Retain 消息存储抽象，便于后续切换为外部存储实现。
 */
public interface RetainStore {

    final class RetainedMessage {
        public final String topic;
        public final byte[] payload;
        public final int qos;

        public RetainedMessage(String topic, byte[] payload, int qos) {
            this.topic = topic;
            this.payload = payload;
            this.qos = qos;
        }
    }

    void put(String topic, byte[] payload, int qos);

    void remove(String topic);

    List<RetainedMessage> list();
}
