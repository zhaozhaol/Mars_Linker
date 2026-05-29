/*
package com.mars.linker.broker.netty;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

*/
/**
 * P0 对拍：比较 EMQX 与 Mars 在核心语义上的一致性（最小可运行版）。
 * <p>
 * 运行示例：
 * mvn -pl mars-linker-broker -Dtest=BrokerParityIntegrationTest test
 * -Demqx.broker=tcp://127.0.0.1:1883
 * -Dmars.broker=tcp://127.0.0.1:11883
 * </p>
 *//*

class BrokerParityIntegrationTest {

    private static final String EMQX_BROKER = System.getProperty("emqx.broker", "tcp://127.0.0.1:1883");
    private static final String MARS_BROKER = System.getProperty("mars.broker", "tcp://127.0.0.1:11883");
    private static final int TIMEOUT_SECONDS = 5;

    @Test
    void should_match_basic_pubsub_behavior() throws Exception {
        boolean emqxReachable = canConnect(EMQX_BROKER);
        boolean marsReachable = canConnect(MARS_BROKER);
        Assumptions.assumeTrue(emqxReachable && marsReachable,
                "Both brokers must be reachable for parity test. emqx=" + emqxReachable + ", mars=" + marsReachable);

        ScenarioResult emqx = runBasicPubSubScenario(EMQX_BROKER);
        ScenarioResult mars = runBasicPubSubScenario(MARS_BROKER);

        assertTrue(emqx.success, "EMQX basic scenario failed: " + emqx.errorMessage);
        assertTrue(mars.success, "Mars basic scenario failed: " + mars.errorMessage);
        assertEquals(emqx.receivedPayload, mars.receivedPayload, "Payload parity mismatch");
        assertNotNull(emqx.receivedPayload, "EMQX should receive payload");
        assertNotNull(mars.receivedPayload, "Mars should receive payload");
    }

    @Test
    void should_match_share_subscription_single_delivery_behavior() throws Exception {
        boolean emqxReachable = canConnect(EMQX_BROKER);
        boolean marsReachable = canConnect(MARS_BROKER);
        Assumptions.assumeTrue(emqxReachable && marsReachable,
                "Both brokers must be reachable for parity test. emqx=" + emqxReachable + ", mars=" + marsReachable);

        ScenarioResult emqx = runShareSubscriptionScenario(EMQX_BROKER);
        ScenarioResult mars = runShareSubscriptionScenario(MARS_BROKER);

        assertTrue(emqx.success, "EMQX share scenario failed: " + emqx.errorMessage);
        assertTrue(mars.success, "Mars share scenario failed: " + mars.errorMessage);
        assertEquals(1, emqx.shareReceiveCount, "EMQX should deliver exactly once in share group");
        assertEquals(1, mars.shareReceiveCount, "Mars should deliver exactly once in share group");
    }

    private static ScenarioResult runBasicPubSubScenario(String broker) {
        MqttClient subscriber = null;
        MqttClient publisher = null;
        try {
            String topic = "p0/parity/basic/" + UUID.randomUUID();
            String payload = "payload-" + UUID.randomUUID();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> received = new AtomicReference<>();

            subscriber = newClient(broker, "sub-basic");
            subscriber.setCallback(new MessageCallback(received, latch));
            connect(subscriber);
            subscriber.subscribe(topic, 1);

            publisher = newClient(broker, "pub-basic");
            connect(publisher);
            publisher.publish(topic, new MqttMessage(payload.getBytes(StandardCharsets.UTF_8)));

            boolean ok = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String got = received.get();
            return ok && payload.equals(got)
                    ? ScenarioResult.success(got, 0)
                    : ScenarioResult.fail("Expected payload not received within timeout, got=" + got);
        } catch (Exception e) {
            return ScenarioResult.fail(e.getMessage());
        } finally {
            closeQuietly(publisher);
            closeQuietly(subscriber);
        }
    }

    private static ScenarioResult runShareSubscriptionScenario(String broker) {
        MqttClient sub1 = null;
        MqttClient sub2 = null;
        MqttClient pub = null;
        try {
            String topic = "p0/parity/share/" + UUID.randomUUID();
            String group = "g1";
            String shareFilter = "$share/" + group + "/" + topic;
            String payload = "payload-" + UUID.randomUUID();

            AtomicInteger recvCount = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(1);

            sub1 = newClient(broker, "sub-share-1");
            sub2 = newClient(broker, "sub-share-2");
            sub1.setCallback(new CountCallback(recvCount, latch));
            sub2.setCallback(new CountCallback(recvCount, latch));
            connect(sub1);
            connect(sub2);
            sub1.subscribe(shareFilter, 1);
            sub2.subscribe(shareFilter, 1);

            pub = newClient(broker, "pub-share");
            connect(pub);
            pub.publish(topic, new MqttMessage(payload.getBytes(StandardCharsets.UTF_8)));

            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int count = recvCount.get();
            return count == 1 ? ScenarioResult.success(payload, count) : ScenarioResult.fail("share receive count=" + count);
        } catch (Exception e) {
            return ScenarioResult.fail(e.getMessage());
        } finally {
            closeQuietly(pub);
            closeQuietly(sub2);
            closeQuietly(sub1);
        }
    }

    private static boolean canConnect(String broker) {
        MqttClient client = null;
        try {
            client = newClient(broker, "probe");
            connect(client);
            return client.isConnected();
        } catch (Exception e) {
            return false;
        } finally {
            closeQuietly(client);
        }
    }

    private static MqttClient newClient(String broker, String role) throws MqttException {
        String clientId = "parity-" + role + "-" + UUID.randomUUID();
        return new MqttClient(broker, clientId, new MemoryPersistence());
    }

    private static void connect(MqttClient client) throws MqttException {
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setAutomaticReconnect(false);
        opts.setConnectionTimeout(TIMEOUT_SECONDS);
        opts.setCleanSession(true);
        client.connect(opts);
    }

    private static void closeQuietly(MqttClient client) {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } catch (Exception ignored) {
            // ignore
        }
        try {
            client.close();
        } catch (Exception ignored) {
            // ignore
        }
    }

    private static final class MessageCallback implements MqttCallback {
        private final AtomicReference<String> payloadRef;
        private final CountDownLatch latch;

        private MessageCallback(AtomicReference<String> payloadRef, CountDownLatch latch) {
            this.payloadRef = payloadRef;
            this.latch = latch;
        }

        @Override
        public void connectionLost(Throwable cause) {
            // noop
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            payloadRef.set(new String(message.getPayload(), StandardCharsets.UTF_8));
            latch.countDown();
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // noop
        }
    }

    private static final class CountCallback implements MqttCallback {
        private final AtomicInteger counter;
        private final CountDownLatch latch;

        private CountCallback(AtomicInteger counter, CountDownLatch latch) {
            this.counter = counter;
            this.latch = latch;
        }

        @Override
        public void connectionLost(Throwable cause) {
            // noop
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            counter.incrementAndGet();
            latch.countDown();
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // noop
        }
    }

    private static final class ScenarioResult {
        private final boolean success;
        private final String receivedPayload;
        private final int shareReceiveCount;
        private final String errorMessage;

        private ScenarioResult(boolean success, String receivedPayload, int shareReceiveCount, String errorMessage) {
            this.success = success;
            this.receivedPayload = receivedPayload;
            this.shareReceiveCount = shareReceiveCount;
            this.errorMessage = errorMessage;
        }

        private static ScenarioResult success(String payload, int shareReceiveCount) {
            return new ScenarioResult(true, payload, shareReceiveCount, null);
        }

        private static ScenarioResult fail(String errorMessage) {
            return new ScenarioResult(false, null, 0, errorMessage);
        }
    }
}
*/
