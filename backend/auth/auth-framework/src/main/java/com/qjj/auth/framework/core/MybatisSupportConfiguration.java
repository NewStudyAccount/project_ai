package com.qjj.auth.framework.core;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qjj.auth.common.result.PageResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.Function;

@Configuration
public class MybatisSupportConfiguration {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(MetaObjectHandler metaObjectHandler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    public static <E, T> PageResult<T> toPageResult(Page<E> page, Function<E, T> mapper) {
        List<T> records = page.getRecords().stream().map(mapper).toList();
        return PageResult.of(records, page.getTotal(), page.getSize(), page.getCurrent());
    }

    public static <E> Page<E> toPage(long current, long size) {
        return new Page<>(current, size);
    }
}
