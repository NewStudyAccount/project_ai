package com.qjj.blog.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 问答会话（knowledge_chat_session；table-design.md §2.4；P0 最小形态，归属用户=审计 create_by）
 */
@Getter
@Setter
@TableName("knowledge_chat_session")
public class KnowledgeChatSession extends KnowledgeBaseEntity {
}
