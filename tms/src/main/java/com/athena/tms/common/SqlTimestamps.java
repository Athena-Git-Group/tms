package com.athena.tms.common;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 同一個 timestamptz 欄位，JDBC 驅動可能回 java.sql.Timestamp，也可能回 OffsetDateTime。 */
public final class SqlTimestamps {

    private SqlTimestamps() {}

    public static OffsetDateTime toOffsetDateTime(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC);
        }
        return (OffsetDateTime) v;
    }
}
