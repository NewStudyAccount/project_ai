package com.qjj.blog.framework.core;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 16 位定长发号（CLAUDE.md 6.4.5）：yyyyMMdd（8 位，Asia/Shanghai 业务日）+ 当日序列（8 位）。
 * 段式取号（步长 500）缓存于内存，重启丢段可接受（不损唯一性）。
 * <p>
 * 取号段必须 {@link TransactionDefinition#PROPAGATION_REQUIRES_NEW} 独立提交：
 * 若与业务事务同回滚，内存已递增而 sys_sequence 未记账，重启/另一实例会重发相同 id（sys_file 主键冲突）。
 * 并发安全用行锁（FOR UPDATE）+ 原子 UPDATE 串行取段。
 * 唯一域 = 本库；sys_sequence 由本类直接 SQL 操作，不走业务 Entity。
 */
@Component
@RequiredArgsConstructor
public class IdGenerator {

    public static final int SEGMENT_STEP = 500;

    private static final long SEQ_RADIX = 100_000_000L;

    private static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JdbcTemplate jdbcTemplate;

    private final PlatformTransactionManager transactionManager;

    private long cachedDayKey;

    private long nextSeq;

    private long maxSeq;

    /**
     * 生成下一个 16 位 id（yyyyMMMdd + 8 位序列拼接的数值形态）。
     */
    public synchronized long nextId() {
        long dayKey = Long.parseLong(LocalDate.now(BIZ_ZONE).format(DAY_FMT));
        if (dayKey != cachedDayKey || nextSeq > maxSeq) {
            fetchSegment(dayKey);
        }
        return dayKey * SEQ_RADIX + nextSeq++;
    }

    /** 业务日数字形态（sys_sequence.id 天然唯一） */
    public long currentDayKey() {
        return Long.parseLong(LocalDate.now(BIZ_ZONE).format(DAY_FMT));
    }

    private void fetchSegment(long dayKey) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Long max = requiresNew.execute(status -> {
            ensureRow(dayKey);
            // MySQL：LAST_INSERT_ID(expr) 写入会话值，UPDATE 与读段上限同一语句完成
            jdbcTemplate.update(
                    "UPDATE sys_sequence SET current_val = LAST_INSERT_ID(current_val + ?) WHERE id = ?",
                    SEGMENT_STEP, dayKey);
            return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        });
        this.cachedDayKey = dayKey;
        this.maxSeq = max == null ? SEGMENT_STEP : max;
        this.nextSeq = this.maxSeq - SEGMENT_STEP + 1;
    }

    private void ensureRow(long dayKey) {
        jdbcTemplate.update("INSERT INTO sys_sequence "
                        + "(id, seq_date, current_val, create_time, update_time, create_by, update_by, deleted) "
                        + "VALUES (?, ?, 0, NOW(), NOW(), 0, 0, 0) "
                        + "ON DUPLICATE KEY UPDATE id = id",
                dayKey, LocalDate.now(BIZ_ZONE).atStartOfDay());
    }
}
