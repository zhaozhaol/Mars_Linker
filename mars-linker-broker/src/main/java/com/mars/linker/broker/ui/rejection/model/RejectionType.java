package com.mars.linker.broker.ui.rejection.model;

public enum RejectionType {
    CONNECT_REFUSED("connect_refused"),
    ACL_SUBSCRIBE_DENIED("acl_subscribe_denied"),
    ACL_PUBLISH_DENIED("acl_publish_denied");

    private final String value;

    RejectionType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static RejectionType fromValue(String value) {
        for (RejectionType t : values()) {
            if (t.value.equals(value)) return t;
        }
        return null;
    }
}
