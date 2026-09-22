package com.example.blog.common.audit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 14 位 ID：yyMMdd(6) + 当日序号(8)。
 */
@Service
public class IdService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter SEQ_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SysSequenceMapper sequenceMapper;

    public IdService(SysSequenceMapper sequenceMapper) {
        this.sequenceMapper = sequenceMapper;
    }

    @Transactional
    public long nextId() {
        return nextId("default");
    }

    @Transactional
    public long nextId(String seqName) {
        LocalDate today = LocalDate.now();
        String seqDate = today.format(SEQ_DAY);
        int updated = sequenceMapper.increment(seqName, seqDate);
        if (updated == 0) {
            ensureRow(seqName, seqDate);
            updated = sequenceMapper.increment(seqName, seqDate);
            if (updated == 0) {
                throw new IllegalStateException("发号失败: " + seqName + "/" + seqDate);
            }
        }
        Long val = sequenceMapper.selectCurrentVal(seqName, seqDate);
        if (val == null) {
            throw new IllegalStateException("发号失败: " + seqName + "/" + seqDate);
        }
        return Long.parseLong(today.format(DAY) + String.format("%08d", val));
    }

    private void ensureRow(String seqName, String seqDate) {
        SysSequence row = new SysSequence();
        row.setId(System.currentTimeMillis());
        row.setSeqName(seqName);
        row.setSeqDate(seqDate);
        row.setCurrentVal(0L);
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        row.setCreateBy(0L);
        row.setUpdateBy(0L);
        row.setDeleted(0);
        sequenceMapper.insert(row);
    }
}
