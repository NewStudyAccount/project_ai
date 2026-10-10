package com.qjj.blog.api.feign;

import com.qjj.blog.api.dto.FileMetaVO;
import com.qjj.blog.api.dto.PingVO;
import com.qjj.blog.common.result.Result;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback：未命中/失败返回降级标记，不抛裸异常。
 */
@Component
public class BlogInternalClientFallback implements FallbackFactory<BlogInternalClient> {

    @Override
    public BlogInternalClient create(Throwable cause) {
        return new BlogInternalClient() {
            @Override
            public Result<FileMetaVO> fileMeta(String id) {
                return Result.ok(null);
            }

            @Override
            public Result<PingVO> ping() {
                PingVO vo = new PingVO();
                vo.setPong(false);
                vo.setDegraded("fallback");
                return Result.ok(vo);
            }
        };
    }
}
