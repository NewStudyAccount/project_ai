package com.qjj.blog.knowledge.ai;

/**
 * LLM 云 API 抽象（blog-knowledge-base 提案 tasks 4.2）：OpenAI 兼容流式对话。
 */
public interface LLMProvider {

    /**
     * 流式对话：增量片段经 deltaConsumer 回调（调用方线程内同步回调）。
     * 失败抛 BizException(203102)；调用方据此进入 KEYWORD_FALLBACK 降级。
     *
     * @param prompt 组装完成的 RAG prompt（system+user 已含检索上下文）
     * @return 模型名（meta 帧透出）
     */
    String chatStream(String prompt, java.util.function.Consumer<String> deltaConsumer);
}
