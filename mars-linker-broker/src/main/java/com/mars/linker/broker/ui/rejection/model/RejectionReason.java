package com.mars.linker.broker.ui.rejection.model;

public enum RejectionReason {
    PROTOCOL_NAME_INVALID("protocol_name_invalid", RejectionType.CONNECT_REFUSED, 0x01),
    UNSUPPORTED_PROTOCOL_LEVEL("unsupported_protocol_level", RejectionType.CONNECT_REFUSED, 0x01),
    CLIENT_ID_EMPTY("client_id_empty", RejectionType.CONNECT_REFUSED, 0x02),
    WILL_QOS2_UNSUPPORTED("will_qos2_unsupported", RejectionType.CONNECT_REFUSED, 0x03),
    AUTH_FAILED("auth_failed", RejectionType.CONNECT_REFUSED, 0x05),
    ACL_SUBSCRIBE_DENIED("acl_subscribe_denied", RejectionType.ACL_SUBSCRIBE_DENIED, null),
    ACL_PUBLISH_DENIED("acl_publish_denied", RejectionType.ACL_PUBLISH_DENIED, null);

    private final String value;
    private final RejectionType type;
    private final Integer connackCode;

    RejectionReason(String value, RejectionType type, Integer connackCode) {
        this.value = value;
        this.type = type;
        this.connackCode = connackCode;
    }

    public String getValue() { return value; }
    public RejectionType getType() { return type; }
    public Integer getConnackCode() { return connackCode; }

    public static RejectionReason fromValue(String value) {
        for (RejectionReason r : values()) {
            if (r.value.equals(value)) return r;
        }
        return null;
    }
}
