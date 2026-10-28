package com.qjj.blog.knowledge.service.impl;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 云 API 调用计数（api-design.md §2.8 providerCalls:{embed,chat}；进程内近似值，重启清零）
 */
@Component
public class KnowledgeMetricsService {

    private final AtomicLong embedCalls = new AtomicLong();
    private final AtomicLong chatCalls = new AtomicLong();

    public void incEmbed() {
        embedCalls.incrementAndGet();
    }

    public void incChat() {
        chatCalls.incrementAndGet();
    }

    public long embedCalls() {
        return embedCalls.get();
    }

    public long chatCalls() {
        return chatCalls.get();
    }
}
