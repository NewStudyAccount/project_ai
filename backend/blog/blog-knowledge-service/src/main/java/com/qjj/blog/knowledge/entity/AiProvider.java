package com.qjj.blog.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 服务商配置（ai_provider；table-design.md §2.3）。
 * kind 维度唯一默认（应用层保证）；api_key dev/test 明文落库，生产加密另开变更。
 */
@Getter
@Setter
@TableName("ai_provider")
public class AiProvider extends KnowledgeBaseEntity {

    /** 服务商标识，系统内唯一 */
    private String code;

    /** 1=Embedding / 2=LLM / 3=兼容 */
    private Integer kind;

    /** SILICONFLOW / DASHSCOPE / ZHIPU / OPENAI */
    private String type;

    /** 显示名 */
    private String name;

    /** 云 API 端点（留空由 yml 引导覆盖） */
    private String baseUrl;

    /** 模型名 */
    private String model;

    /** 向量维度（kind 含 Embedding 时必填） */
    private Integer dim;

    /** dev/test 明文或引导留空；生产加密另开变更 */
    private String apiKey;

    /** kind 维度唯一默认（应用层保证） */
    private Integer isDefault;

    /** 1 启用 / 0 停用 */
    private Integer status;
}
