package com.example.blog.common.audit;

import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;

@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long uid = CurrentUser.idOrNull();
        long createBy = uid == null ? 0L : uid;
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "createBy", Long.class, createBy);
        strictInsertFill(metaObject, "updateBy", Long.class, createBy);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        if (CurrentUser.idOrNull() != null) {
            strictUpdateFill(metaObject, "updateBy", Long.class, CurrentUser.idOrNull());
        }
    }
}
