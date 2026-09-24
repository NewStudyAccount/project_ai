package com.qjj.user.framework.core;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.qjj.user.common.result.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.Function;

/**
 * MyBatis-Plus 统一装配（分页插件 + 审计填充）；与 MP Page 互转在此（CLAUDE.md 6.4.1）。
 */
@Configuration
public class MybatisSupportConfiguration {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(MetaObjectHandler metaObjectHandler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /** MP Page → 统一分页契约 */
    public static <E, T> PageResult<T> toPageResult(Page<E> page, Function<E, T> mapper) {
        List<T> records = page.getRecords().stream().map(mapper).toList();
        return PageResult.of(records, page.getTotal(), page.getSize(), page.getCurrent());
    }

    /** 分页入参 → MP Page */
    public static <E> Page<E> toPage(long current, long size) {
        return new Page<>(current, size);
    }
}
