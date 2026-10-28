package com.qjj.blog.knowledge.controller;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.common.result.Result;
import com.qjj.blog.knowledge.dto.TaskPageQuery;
import com.qjj.blog.knowledge.service.KnowledgeStatsService;
import com.qjj.blog.knowledge.service.KnowledgeTaskService;
import com.qjj.blog.knowledge.vo.KnowledgeStatsVO;
import com.qjj.blog.knowledge.vo.TaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 · 任务观测与统计 API（api-design.md §2.7/§2.8；权限位 blog:knowledge:manage）
 */
@Tag(name = "知识库 · 任务与统计", description = "任务分页/重跑/统计与 ES 健康度")
@RestController
@RequestMapping("/api/v1/admin/knowledge")
@RequiredArgsConstructor
public class KnowledgeTaskController {

    private final KnowledgeTaskService taskService;
    private final KnowledgeStatsService statsService;

    @Operation(summary = "任务分页（taskType/status/docId 筛选）")
    @GetMapping("/tasks")
    public Result<PageResult<TaskVO>> page(TaskPageQuery query) {
        return Result.ok(taskService.page(query));
    }

    @Operation(summary = "重跑失败任务（仅 FAILED/DEAD）")
    @PostMapping("/tasks/{id}/rerun")
    public Result<TaskVO> rerun(@PathVariable String id) {
        return Result.ok(taskService.rerun(id));
    }

    @Operation(summary = "统计（doc/chunk 分布 + ES 健康度，ES 不可用仍 200）")
    @GetMapping("/stats")
    public Result<KnowledgeStatsVO> stats() {
        return Result.ok(statsService.stats());
    }
}
