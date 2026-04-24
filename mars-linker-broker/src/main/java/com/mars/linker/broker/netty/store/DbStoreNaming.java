package com.mars.linker.broker.netty.store;

public final class DbStoreNaming {
    private DbStoreNaming() {
    }

    public static String prefix(String schema, String tablePrefix) {
        String p = tablePrefix == null ? "ml_" : tablePrefix.trim();
        if (p.isEmpty()) {
            p = "ml_";
        }
        String s = schema == null ? "" : schema.trim();
        if (s.isEmpty()) {
            return p;
        }
        return s + "." + p;
    }

    public static String createTableIfNotExists(String fullTableName, String ddlBody) {
        return "CREATE TABLE IF NOT EXISTS " + fullTableName + " " + ddlBody;
    }
}

