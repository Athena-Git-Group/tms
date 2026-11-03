package com.athena.tms.common;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 把 JDBC 查詢結果的時間欄位統一轉成 {@link OffsetDateTime}。同一個
 * timestamptz 欄位，驅動可能回 java.sql.Timestamp，也可能回
 * OffsetDateTime；呼叫端若直接轉型，遇到另一種型別就會失敗。
 */
public final class SqlTimestamps {

    private SqlTimestamps() {}

    /** null 原樣回 null，交由呼叫端決定缺值怎麼處理；其他非時間型別直接轉型失敗。 */
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
