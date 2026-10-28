package com.qjj.blog.knowledge.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 从文章入库入参（api-design.md §1.4；≤100 条/次，超出 30001）
 */
@Getter
@Setter
public class FromArticlesRequest {

    @NotEmpty(message = "articleIds 不能为空")
    @Size(max = 100, message = "单次最多入库 100 篇文章")
    private List<String> articleIds = new ArrayList<>();
}
