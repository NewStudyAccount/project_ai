package com.example.file.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.example.file.security.CurrentUser;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        long uid = CurrentUser.idOrNull() == null ? 0L : CurrentUser.idOrNull();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "createBy", Long.class, uid);
        strictInsertFill(metaObject, "updateBy", Long.class, uid);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        if (CurrentUser.idOrNull() != null) {
            strictUpdateFill(metaObject, "updateBy", Long.class, CurrentUser.idOrNull());
        }
    }
}
