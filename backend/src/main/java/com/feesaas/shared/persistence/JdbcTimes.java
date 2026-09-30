package com.feesaas.shared.persistence;

import java.sql.Timestamp;
import java.time.Instant;

/** PostgreSQL JDBC cannot infer a type for {@link Instant}; bind {@link Timestamp} instead. */
public final class JdbcTimes {
    private JdbcTimes() {}

    public static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
