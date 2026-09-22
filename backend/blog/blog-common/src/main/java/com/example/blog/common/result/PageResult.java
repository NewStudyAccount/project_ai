package com.example.blog.common.result;

import java.util.Collections;
import java.util.List;

/** 分页返回体 {records,total,size,current}。 */
public record PageResult<T>(List<T> records, long total, long size, long current) {

    public static <T> PageResult<T> of(List<T> records, long total, long size, long current) {
        return new PageResult<>(
                records == null ? Collections.emptyList() : records,
                total,
                size,
                current);
    }

    public static <T> PageResult<T> empty(long size, long current) {
        return of(Collections.emptyList(), 0, size, current);
    }
}
