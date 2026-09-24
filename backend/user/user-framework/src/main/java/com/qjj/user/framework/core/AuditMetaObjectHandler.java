package com.qjj.user.framework.core;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计 5 字段自动填充（CLAUDE.md 6.4.5）：create_time / update_time / create_by / update_by。
 * deleted 由列默认值 + @TableLogic 管理；禁止业务手写审计/逻辑删除赋值。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        Long operator = UserContext.userIdOrSystem();
        strictInsertFill(metaObject, "createBy", Long.class, operator);
        strictInsertFill(metaObject, "updateBy", Long.class, operator);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        strictUpdateFill(metaObject, "updateBy", Long.class, UserContext.userIdOrSystem());
    }
}
