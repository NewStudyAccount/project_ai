package com.example.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.auth.api.UserApis;
import com.example.auth.entity.SysDept;
import com.example.auth.entity.SysUser;
import com.example.auth.mapper.SysDeptMapper;
import com.example.auth.mapper.SysUserMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户只读查询（出参无凭证）。 */
@Service
public class UserQueryService {

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;

    public UserQueryService(SysUserMapper userMapper, SysDeptMapper deptMapper) {
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
    }

    @Transactional(readOnly = true)
    public UserApis.UserPageResult search(String keyword, long current, long size) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            qw.like(SysUser::getUsername, like).or().like(SysUser::getRealName, like);
        }
        qw.orderByDesc(SysUser::getId);
        long total = userMapper.selectCount(qw);
        // 简化分页（管理端规模）
        List<SysUser> users = userMapper.selectList(qw.last(
                "LIMIT " + Math.max(1, size) + " OFFSET " + Math.max(0, (current - 1) * size)));
        return new UserApis.UserPageResult(toBrief(users), total, size, current);
    }

    @Transactional(readOnly = true)
    public List<UserApis.UserBrief> batch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getId, ids));
        return toBrief(users);
    }

    private List<UserApis.UserBrief> toBrief(List<SysUser> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        Map<Long, String> deptNames = loadDeptNames(users);
        return users.stream().map(u -> new UserApis.UserBrief(
                u.getId(),
                u.getUsername(),
                u.getRealName(),
                u.getStatus(),
                u.getDeptId(),
                deptNames.getOrDefault(Objects.requireNonNullElse(u.getDeptId(), 0L), "")
        )).toList();
    }

    private Map<Long, String> loadDeptNames(List<SysUser> users) {
        List<Long> deptIds = users.stream()
                .map(SysUser::getDeptId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (deptIds.isEmpty()) {
            return new HashMap<>();
        }
        List<SysDept> depts = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .in(SysDept::getId, deptIds));
        Map<Long, String> map = new HashMap<>();
        depts.forEach(d -> map.put(d.getId(), d.getDeptName()));
        return map;
    }
}
