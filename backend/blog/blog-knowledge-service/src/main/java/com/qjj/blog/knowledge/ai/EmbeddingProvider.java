package com.qjj.blog.knowledge.ai;

import java.util.List;

/**
 * Embedding 云 API 抽象（blog-knowledge-base 提案 tasks 4.2）。
 * 实现须批量接口 + 线程安全；失败抛 BizException(203101)。
 */
public interface EmbeddingProvider {

    /**
     * 批量向量化（顺序与入参一致）。
     *
     * @param texts 文本列表（含上下文增强前缀）
     * @return 向量列表（dim 与 ai_provider.dim / ES index dims 一致）
     */
    List<float[]> embed(List<String> texts);
}
