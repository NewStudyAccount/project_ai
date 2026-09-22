package com.example.auth.common;

import java.util.List;

/**
 * 分页契约 {records,total,size,current}。
 */
public record PageResult<T>(List<T> records, long total, long size, long current) {

    public static <T> PageResult<T> of(List<T> records, long total, long size, long current) {
        return new PageResult<>(records, total, size, current);
    }
}
