package com.mars.linker.broker.ui.rejection;

import com.mars.linker.broker.ui.rejection.model.RejectionMessage;
import com.mars.linker.broker.ui.rejection.model.RejectionReason;
import com.mars.linker.broker.ui.rejection.model.RejectionType;
import org.springframework.stereotype.Component;

@Component
public class RejectionMessageCollector {

    private final RejectionMessageStore store;

    public RejectionMessageCollector(RejectionMessageStore store) {
        this.store = store;
    }

    public void collectConnectRefused(String clientId, RejectionReason reason, Integer connackCode, String remoteAddress) {
        String effectiveClientId = clientId != null ? clientId : remoteAddress;
        String detail = "CONNACK code=0x" + (connackCode != null ? Integer.toHexString(connackCode) : "unknown");
        RejectionMessage msg = RejectionMessage.builder()
                .clientId(effectiveClientId != null ? effectiveClientId : "unknown")
                .type(RejectionType.CONNECT_REFUSED)
                .reason(reason)
                .connackCode(connackCode)
                .detail(detail)
                .remoteAddress(remoteAddress)
                .build();
        store.add(msg);
    }

    public void collectAclSubscribeDenied(String clientId, String topicFilter, String remoteAddress) {
        RejectionMessage msg = RejectionMessage.builder()
                .clientId(clientId != null ? clientId : (remoteAddress != null ? remoteAddress : "unknown"))
                .type(RejectionType.ACL_SUBSCRIBE_DENIED)
                .reason(RejectionReason.ACL_SUBSCRIBE_DENIED)
                .detail("topicFilter=" + topicFilter)
                .remoteAddress(remoteAddress)
                .build();
        store.add(msg);
    }

    public void collectAclPublishDenied(String clientId, String topic, String remoteAddress) {
        RejectionMessage msg = RejectionMessage.builder()
                .clientId(clientId != null ? clientId : (remoteAddress != null ? remoteAddress : "unknown"))
                .type(RejectionType.ACL_PUBLISH_DENIED)
                .reason(RejectionReason.ACL_PUBLISH_DENIED)
                .detail("topic=" + topic)
                .remoteAddress(remoteAddress)
                .build();
        store.add(msg);
    }
}
