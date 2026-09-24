package com.qjj.auth.framework.core;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class IdGenerator {
    public static final int SEGMENT_STEP = 500;
    private static final long SEQ_RADIX = 100_000_000L;
    private static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private long cachedDayKey;
    private long nextSeq;
    private long maxSeq;

    public synchronized long nextId() {
        long dayKey = Long.parseLong(LocalDate.now(BIZ_ZONE).format(DAY_FMT));
        if (dayKey != cachedDayKey || nextSeq > maxSeq) {
            fetchSegment(dayKey);
        }
        return dayKey * SEQ_RADIX + nextSeq++;
    }

    private void fetchSegment(long dayKey) {
        Long max = transactionTemplate.execute(status -> {
            jdbcTemplate.update("INSERT INTO sys_sequence (id, seq_date, current_val, create_time, update_time, create_by, update_by, deleted) "
                    + "SELECT ?, ?, 0, NOW(), NOW(), 0, 0, 0 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_sequence WHERE id = ?)",
                    dayKey, LocalDate.now(BIZ_ZONE).atStartOfDay(), dayKey);
            jdbcTemplate.queryForObject("SELECT current_val FROM sys_sequence WHERE id = ? FOR UPDATE", Long.class, dayKey);
            jdbcTemplate.update("UPDATE sys_sequence SET current_val = current_val + ? WHERE id = ?", SEGMENT_STEP, dayKey);
            return jdbcTemplate.queryForObject("SELECT current_val FROM sys_sequence WHERE id = ?", Long.class, dayKey);
        });
        cachedDayKey = dayKey;
        maxSeq = max == null ? SEGMENT_STEP : max;
        nextSeq = maxSeq - SEGMENT_STEP + 1;
    }
}
