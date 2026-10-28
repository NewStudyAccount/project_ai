package com.qjj.blog.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 问答消息（knowledge_chat_message；table-design.md §2.5；refs JSON 数组 [{chunkId,docId,docTitle,docUrl,score}]）
 */
@Getter
@Setter
@TableName("knowledge_chat_message")
public class KnowledgeChatMessage extends KnowledgeBaseEntity {

    /** → knowledge_chat_session.id */
    private Long sessionId;

    /** Q 提问 / A 回答 */
    private String role;

    /** 消息正文（回答为完整文本，非流式片段） */
    private String content;

    /** 引用来源 JSON（A 行；Q 行为空） */
    private String refs;

    /** 生成耗时 ms（Q 为 0） */
    private Integer costMs;
}
