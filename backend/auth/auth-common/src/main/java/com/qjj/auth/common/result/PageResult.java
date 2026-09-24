package com.qjj.auth.common.result;

import java.util.Collections;
import java.util.List;
import lombok.Getter;

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
}
