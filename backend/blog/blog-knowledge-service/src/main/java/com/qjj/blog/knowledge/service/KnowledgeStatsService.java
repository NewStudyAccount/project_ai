package com.qjj.blog.knowledge.service;

import com.qjj.blog.knowledge.vo.KnowledgeStatsVO;

/**
 * 知识库统计（api-design.md §2.8）
 */
public interface KnowledgeStatsService {

    KnowledgeStatsVO stats();
}
