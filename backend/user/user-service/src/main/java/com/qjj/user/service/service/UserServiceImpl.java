package com.qjj.user.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qjj.user.api.dto.UserBasicVO;
import com.qjj.user.api.dto.UserProfileVO;
import com.qjj.user.api.dto.UsernameStatusVO;
import com.qjj.user.common.enums.UserErrorCodeEnum;
import com.qjj.user.common.exception.BizException;
import com.qjj.user.common.result.PageResult;
import com.qjj.user.common.util.DesensitizedUtils;
import com.qjj.user.framework.core.IdGenerator;
import com.qjj.user.framework.core.MybatisSupportConfiguration;
import com.qjj.user.service.dto.CreateUserRequest;
import com.qjj.user.service.dto.UpdateStatusRequest;
import com.qjj.user.service.dto.UpdateUserRequest;
import com.qjj.user.service.dto.UserPageQuery;
import com.qjj.user.service.entity.SysUser;
import com.qjj.user.service.entity.SysUserProfile;
import com.qjj.user.service.enums.AuditActionEnum;
import com.qjj.user.service.enums.UserStatusEnum;
import com.qjj.user.service.mapper.SysUserMapper;
import com.qjj.user.service.mapper.SysUserProfileMapper;
import com.qjj.user.service.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final java.util.Set<String> ORDER_FIELDS = java.util.Set.of("createTime", "updateTime", "username", "status");

    private final SysUserMapper userMapper;

    private final SysUserProfileMapper profileMapper;

    private final IdGenerator idGenerator;

    private final AuditService auditService;

    @Override
    @Transactional
    public UserVO create(CreateUserRequest request) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(UserErrorCodeEnum.USERNAME_EXISTS);
        }
        long id = idGenerator.nextId();
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(request.getUsername());
        user.setRealName(defaultString(request.getRealName()));
        user.setNickname(defaultString(request.getNickname()));
        user.setEmail(defaultString(request.getEmail()));
        user.setPhone(defaultString(request.getPhone()));
        user.setAvatar(defaultString(request.getAvatar()));
        user.setStatus(UserStatusEnum.NORMAL.getCode());
        user.setRemark(defaultString(request.getRemark()));
        userMapper.insert(user);
        SysUserProfile profile = new SysUserProfile();
        profile.setId(idGenerator.nextId());
        profile.setUserId(id);
        profile.setGender(0);
        profile.setAddress("");
        profile.setExtraJson("");
        profileMapper.insert(profile);
        auditService.record(AuditActionEnum.USER_CREATE, id, "创建用户 " + user.getUsername());
        return toVO(user, profile);
    }

    @Override
    @Transactional
    public UserVO update(String idValue, UpdateUserRequest request) {
        long id = parseId(idValue);
        SysUser user = requireUser(id);
        user.setRealName(nonNull(request.getRealName(), user.getRealName()));
        user.setNickname(nonNull(request.getNickname(), user.getNickname()));
        user.setEmail(nonNull(request.getEmail(), user.getEmail()));
        user.setPhone(nonNull(request.getPhone(), user.getPhone()));
        user.setAvatar(nonNull(request.getAvatar(), user.getAvatar()));
        user.setRemark(nonNull(request.getRemark(), user.getRemark()));
        userMapper.updateById(user);
        SysUserProfile profile = requireProfile(id);
        if (request.getGender() != null) profile.setGender(request.getGender());
        if (request.getBirthday() != null) profile.setBirthday(request.getBirthday());
        if (request.getAddress() != null) profile.setAddress(request.getAddress());
        if (request.getExtraJson() != null) profile.setExtraJson(request.getExtraJson());
        profileMapper.updateById(profile);
        auditService.record(AuditActionEnum.USER_UPDATE, id, "更新用户资料");
        return toVO(user, profile);
    }

    @Override
    @Transactional
    public UserVO updateStatus(String idValue, UpdateStatusRequest request) {
        long id = parseId(idValue);
        SysUser user = requireUser(id);
        user.setStatus(request.getStatus());
        userMapper.updateById(user);
        auditService.record(AuditActionEnum.USER_STATUS, id, "状态变更 " + request.getStatus());
        return toVO(user, requireProfile(id));
    }

    @Override
    public UserVO get(String idValue) {
        long id = parseId(idValue);
        return toVO(requireUser(id), requireProfile(id));
    }

    @Override
    public PageResult<UserVO> page(UserPageQuery query) {
        String orderBy = ORDER_FIELDS.contains(query.getOrderBy()) ? query.getOrderBy() : "createTime";
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .like(query.getUsername() != null && !query.getUsername().isBlank(), SysUser::getUsername, query.getUsername())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus());
        boolean desc = "desc".equalsIgnoreCase(query.getOrder());
        switch (orderBy) {
            case "username" -> applyOrder(wrapper, SysUser::getUsername, desc);
            case "status" -> applyOrder(wrapper, SysUser::getStatus, desc);
            case "updateTime" -> applyOrder(wrapper, SysUser::getUpdateTime, desc);
            default -> applyOrder(wrapper, SysUser::getCreateTime, desc);
        }
        Page<SysUser> page = userMapper.selectPage(MybatisSupportConfiguration.toPage(query.getCurrent(), query.getSize()), wrapper);
        return MybatisSupportConfiguration.toPageResult(page, user -> toVO(user, findProfile(user.getId())));
    }

    @Override
    public UserBasicVO getBasic(String idValue) {
        SysUser user = requireUser(parseId(idValue));
        UserBasicVO vo = new UserBasicVO();
        vo.setId(String.valueOf(user.getId()));
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setNickname(user.getNickname());
        vo.setEmail(DesensitizedUtils.email(user.getEmail()));
        vo.setPhone(DesensitizedUtils.phone(user.getPhone()));
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setRemark(user.getRemark());
        return vo;
    }

    @Override
    public UsernameStatusVO getByUsername(String username) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            return new UsernameStatusVO();
        }
        UsernameStatusVO vo = new UsernameStatusVO();
        vo.setId(String.valueOf(user.getId()));
        vo.setUsername(user.getUsername());
        vo.setStatus(user.getStatus());
        return vo;
    }

    @Override
    public UserProfileVO getProfile(String idValue) {
        long id = parseId(idValue);
        SysUser user = requireUser(id);
        SysUserProfile profile = findProfile(id);
        UserProfileVO vo = new UserProfileVO();
        vo.setId(String.valueOf(user.getId()));
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setNickname(user.getNickname());
        vo.setEmail(DesensitizedUtils.email(user.getEmail()));
        vo.setPhone(DesensitizedUtils.phone(user.getPhone()));
        vo.setAvatar(user.getAvatar());
        if (profile != null) {
            vo.setGender(profile.getGender());
            vo.setBirthday(profile.getBirthday() == null ? "" : profile.getBirthday().toString());
            vo.setAddress(profile.getAddress());
            vo.setExtraJson(profile.getExtraJson());
        }
        return vo;
    }

    private SysUser requireUser(long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(UserErrorCodeEnum.USER_NOT_FOUND);
        }
        return user;
    }

    private SysUserProfile requireProfile(long id) {
        SysUserProfile profile = findProfile(id);
        if (profile == null) {
            throw new BizException(UserErrorCodeEnum.USER_NOT_FOUND);
        }
        return profile;
    }

    private SysUserProfile findProfile(Long userId) {
        return profileMapper.selectOne(new LambdaQueryWrapper<SysUserProfile>().eq(SysUserProfile::getUserId, userId));
    }

    private UserVO toVO(SysUser user, SysUserProfile profile) {
        UserVO vo = new UserVO();
        vo.setId(String.valueOf(user.getId()));
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setNickname(user.getNickname());
        vo.setEmail(DesensitizedUtils.email(user.getEmail()));
        vo.setPhone(DesensitizedUtils.phone(user.getPhone()));
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setRemark(user.getRemark());
        if (profile != null) {
            vo.setGender(profile.getGender());
            vo.setBirthday(profile.getBirthday());
            vo.setAddress(profile.getAddress());
            vo.setExtraJson(profile.getExtraJson());
        }
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }

    private long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BizException(UserErrorCodeEnum.USER_NOT_FOUND);
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String nonNull(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private void applyOrder(LambdaQueryWrapper<SysUser> wrapper,
                            com.baomidou.mybatisplus.core.toolkit.support.SFunction<SysUser, ?> column,
                            boolean desc) {
        if (desc) {
            wrapper.orderByDesc(column);
        } else {
            wrapper.orderByAsc(column);
        }
    }
}
