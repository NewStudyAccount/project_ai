package com.qjj.blog.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 问知识库入参（api-design.md §4.1：question 1–500 字；sessionId 可选，仅用于会话落库）
 */
@Getter
@Setter
public class AskRequest {

    @NotBlank(message = "question 不能为空")
    @Size(min = 1, max = 500, message = "问题长度须在 1–500 字之间")
    private String question;

    /** 续用既有会话；空=新建并首帧回传 */
    private String sessionId;
}
