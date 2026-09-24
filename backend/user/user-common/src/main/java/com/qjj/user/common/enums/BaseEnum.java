package com.qjj.user.common.enums;

/**
 * 枚举基类契约（CLAUDE.md 6.13）：库里存 code，界面显示 desc，接口只传 code。
 */
public interface BaseEnum<C> {

    C getCode();

    String getDesc();
}
