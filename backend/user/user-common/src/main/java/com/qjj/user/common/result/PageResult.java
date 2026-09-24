package com.qjj.user.common.result;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 分页契约（CLAUDE.md 6.4.1）：{records, total, size, current}
 */
@Getter
public class PageResult<T> {

    private List<T> records;

    private long total;

    private long size;

    private long current;

    public static <T> PageResult<T> of(List<T> records, long total, long size, long current) {
        PageResult<T> p = new PageResult<>();
        p.records = records == null ? Collections.emptyList() : records;
        p.total = total;
        p.size = size;
        p.current = current;
        return p;
    }

    public static <T> PageResult<T> empty(long size, long current) {
        return of(Collections.emptyList(), 0L, size, current);
    }
}
