package com.qjj.blog.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识库域业务表统一审计 5 字段与逻辑删除约定（对齐 blog-service BaseEntity 口径；
 * 独立声明：knowledge 模块不依赖 blog-service，审计填充经 framework AuditMetaObjectHandler 按字段名生效）。
 */
@Getter
@Setter
public abstract class KnowledgeBaseEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableLogic
    private Integer deleted;
}
