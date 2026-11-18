package com.athena.tms.common;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 把 timestamptz 欄位讀出的值統一轉成 OffsetDateTime。同一個欄位，JDBC 驅動可能回
 * java.sql.Timestamp，也可能回 OffsetDateTime，呼叫端不必各自判斷。
 */
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
