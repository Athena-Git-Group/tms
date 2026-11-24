package com.athena.tms.common;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.ColumnMapRowMapper;

/**
 * 給以欄位名取值的查詢用：timestamptz 欄位讀成 {@link OffsetDateTime}，呼叫端可以直接轉型使用。
 * Spring 預設的 {@link ColumnMapRowMapper} 會把 timestamptz 讀成 {@code java.sql.Timestamp}。
 */
public final class OffsetDateTimeColumnMapRowMapper extends ColumnMapRowMapper {

    public static final OffsetDateTimeColumnMapRowMapper INSTANCE = new OffsetDateTimeColumnMapRowMapper();

    private OffsetDateTimeColumnMapRowMapper() {}

    @Override
    protected Object getColumnValue(ResultSet rs, int index) throws SQLException {
        if ("timestamptz".equals(rs.getMetaData().getColumnTypeName(index))) {
            return rs.getObject(index, OffsetDateTime.class);
        }
        return super.getColumnValue(rs, index);
    }
}
