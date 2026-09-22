package com.example.blog.content.service;

import com.example.blog.content.entity.SysUserRef;
import com.example.blog.content.mapper.SysUserRefMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户展示投影：登录 upsert；展示 batch 补洞。投影失败不影响授权。
 */
@Service
public class UserRefService {

    private final SysUserRefMapper userRefMapper;

    public UserRefService(SysUserRefMapper userRefMapper) {
        this.userRefMapper = userRefMapper;
    }

    @Transactional
    public void upsertFromLogin(Long userId, String username, String realName) {
        if (userId == null) {
            return;
        }
        SysUserRef row = new SysUserRef();
        row.setUserId(userId);
        row.setUsername(username == null ? "" : username);
        row.setRealName(realName == null ? "" : realName);
        row.setStatus(1);
        row.setDeptId(0L);
        row.setDeptName("");
        row.setSyncTime(LocalDateTime.now());
        row.setSourceVersion(0L);
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        userRefMapper.upsert(row);
    }

    /** 展示用 batch：命中投影优先，缺的用占位补洞，不抛错。 */
    public Map<Long, SysUserRef> batchForDisplay(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<SysUserRef> rows = userRefMapper.selectByUserIds(userIds);
        Map<Long, SysUserRef> map = rows.stream()
                .collect(Collectors.toMap(SysUserRef::getUserId, Function.identity(), (a, b) -> a));
        for (Long id : userIds) {
            map.computeIfAbsent(id, uid -> {
                SysUserRef stub = new SysUserRef();
                stub.setUserId(uid);
                stub.setUsername("");
                stub.setRealName("用户" + uid);
                stub.setStatus(1);
                return stub;
            });
        }
        return map;
    }

    public SysUserRef getByUserId(Long userId) {
        return userRefMapper.selectByUserId(userId);
    }
}
