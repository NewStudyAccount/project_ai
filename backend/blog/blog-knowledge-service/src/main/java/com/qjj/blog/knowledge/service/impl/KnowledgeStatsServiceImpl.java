package com.qjj.blog.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qjj.blog.knowledge.entity.KnowledgeChunk;
import com.qjj.blog.knowledge.entity.KnowledgeDoc;
import com.qjj.blog.knowledge.entity.KnowledgeTask;
import com.qjj.blog.knowledge.enums.KnowledgeDocStatusEnum;
import com.qjj.blog.knowledge.enums.KnowledgeEmbedStatusEnum;
import com.qjj.blog.knowledge.enums.KnowledgeTaskStatusEnum;
import com.qjj.blog.knowledge.es.KnowledgeChunkIndexService;
import com.qjj.blog.knowledge.mapper.KnowledgeChunkMapper;
import com.qjj.blog.knowledge.mapper.KnowledgeDocMapper;
import com.qjj.blog.knowledge.mapper.KnowledgeTaskMapper;
import com.qjj.blog.knowledge.service.KnowledgeStatsService;
import com.qjj.blog.knowledge.vo.KnowledgeStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库统计实现（api-design.md §2.8）：ES 不可用容错（esDocCount=null/esHealthy=false 仍 200）。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeStatsServiceImpl implements KnowledgeStatsService {

    private final KnowledgeDocMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeTaskMapper taskMapper;
    private final KnowledgeChunkIndexService chunkIndexService;
    private final KnowledgeMetricsService metrics;

    @Override
    public KnowledgeStatsVO stats() {
        KnowledgeStatsVO vo = new KnowledgeStatsVO();
        vo.setDocTotal(docMapper.selectCount(null));
        for (KnowledgeDocStatusEnum s : KnowledgeDocStatusEnum.values()) {
            Long c = docMapper.selectCount(new LambdaQueryWrapper<KnowledgeDoc>()
                    .eq(KnowledgeDoc::getStatus, s.getCode()));
            vo.getDocByStatus().put(s.name().toLowerCase(), c == null ? 0 : c);
        }
        vo.setChunkTotal(chunkMapper.selectCount(null));
        Long ready = chunkMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getEmbedStatus, KnowledgeEmbedStatusEnum.READY.getCode()));
        long readyCount = ready == null ? 0 : ready;
        vo.setChunkReadyRatio(vo.getChunkTotal() == 0 ? 0d : (double) readyCount / vo.getChunkTotal());
        Long taskFailed = taskMapper.selectCount(new LambdaQueryWrapper<KnowledgeTask>()
                .eq(KnowledgeTask::getStatus, KnowledgeTaskStatusEnum.FAILED.getCode()));
        vo.setTaskFailed(taskFailed == null ? 0 : taskFailed);
        vo.setEsHealthy(chunkIndexService.healthy());
        vo.setEsDocCount(vo.getEsHealthy() ? chunkIndexService.countOrNull() : null);
        vo.getProviderCalls().put("embed", metrics.embedCalls());
        vo.getProviderCalls().put("chat", metrics.chatCalls());
        return vo;
    }
}
