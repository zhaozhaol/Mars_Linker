package com.mars.linker.broker.netty;

final class DbStoreNaming {
    private DbStoreNaming() {
    }

    static String prefix(String schema, String tablePrefix) {
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

    static String createTableIfNotExists(String fullTableName, String ddlBody) {
        return "CREATE TABLE IF NOT EXISTS " + fullTableName + " " + ddlBody;
    }
}

