package com.qjj.blog.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qjj.blog.common.enums.BlogErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.framework.idempotent.Idempotent;
import com.qjj.blog.framework.idempotent.RateLimit;
import com.qjj.blog.objectstorage.ObjectStorageClient;
import com.qjj.blog.objectstorage.ObjectStorageException;
import com.qjj.blog.objectstorage.ProviderClientRegistry;
import com.qjj.blog.objectstorage.SecretKeyCipher;
import com.qjj.blog.service.dto.StorageProviderPageQuery;
import com.qjj.blog.service.dto.StorageProviderRequest;
import com.qjj.blog.service.entity.SysFile;
import com.qjj.blog.service.entity.SysStorageProvider;
import com.qjj.blog.service.enums.AuditActionEnum;
import com.qjj.blog.service.enums.StorageProviderTypeEnum;
import com.qjj.blog.service.mapper.SysFileMapper;
import com.qjj.blog.service.mapper.SysStorageProviderMapper;
import com.qjj.blog.service.service.AuditService;
import com.qjj.blog.service.service.StorageProviderService;
import com.qjj.blog.service.vo.StorageProviderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 对象存储服务商配置管理（object-storage-design.md §5.2/§8.2/§9）。
 * SK 加密落库；出参脱敏；变更后失效客户端缓存；删除受引用文件保护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageProviderServiceImpl implements StorageProviderService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<Integer> VALID_STATUS = Set.of(0, 1);
    private static final Set<Integer> VALID_PATH_STYLE = Set.of(0, 1);

    private final SysStorageProviderMapper providerMapper;
    private final SysFileMapper fileMapper;
    private final IdGenerator idGenerator;
    private final SecretKeyCipher cipher;
    private final ObjectStorageClient storageClient;
    private final ProviderClientRegistry registry;
    private final AuditService auditService;

    @Override
    @RateLimit(keyPrefix = "storage-provider-list", permits = 60, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public PageResult<StorageProviderVO> page(StorageProviderPageQuery query) {
        long current = query.getCurrent() < 1 ? 1 : query.getCurrent();
        long size = query.getSize() < 1 ? 10 : Math.min(query.getSize(), 200);
        LambdaQueryWrapper<SysStorageProvider> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = query.getKeyword();
            wrapper.and(w -> w.like(SysStorageProvider::getCode, kw)
                    .or().like(SysStorageProvider::getName, kw));
        }
        if (query.getProviderType() != null && !query.getProviderType().isBlank()) {
            wrapper.eq(SysStorageProvider::getProviderType, query.getProviderType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysStorageProvider::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(SysStorageProvider::getCreateTime).orderByDesc(SysStorageProvider::getId);
        Page<SysStorageProvider> page = providerMapper.selectPage(new Page<>(current, size), wrapper);
        List<StorageProviderVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, page.getTotal(), size, current);
    }

    @Override
    public StorageProviderVO get(String id) {
        return toVO(require(id));
    }

    @Override
    @Transactional
    @RateLimit(keyPrefix = "storage-provider-write", permits = 20, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public StorageProviderVO create(StorageProviderRequest request) {
        StorageProviderTypeEnum type = validate(request);
        // code 唯一
        Long count = providerMapper.selectCount(new LambdaQueryWrapper<SysStorageProvider>()
                .eq(SysStorageProvider::getCode, request.getCode()));
        if (count != null && count > 0) {
            throw new BizException(BlogErrorCodeEnum.PROVIDER_CODE_EXISTS);
        }
        if (request.getSecretKey() == null || request.getSecretKey().isBlank()) {
            throw new BizException(BlogErrorCodeEnum.PROVIDER_CONFIG_INVALID,
                    "secretKey 不能为空（创建时必填，将加密存储）");
        }
        SysStorageProvider entity = new SysStorageProvider();
        entity.setId(idGenerator.nextId());
        entity.setCode(request.getCode());
        entity.setName(request.getName() == null ? "" : request.getName());
        entity.setProviderType(type.getCode());
        entity.setEndpoint(request.getEndpoint());
        entity.setRegion(request.getRegion() == null ? "" : request.getRegion());
        entity.setBucket(request.getBucket());
        entity.setAccessKey(request.getAccessKey());
        entity.setSecretKey(encryptSafely(request.getSecretKey()));
        entity.setPathStyle(normalizePathStyle(request.getPathStyle()));
        entity.setPublicBaseUrl(request.getPublicBaseUrl());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        entity.setIsDefault(request.getIsDefault() == null ? 0 : request.getIsDefault());
        entity.setDescription(request.getDescription() == null ? "" : request.getDescription());
        // 若设为默认，先清空其他默认
        if (entity.getIsDefault() != null && entity.getIsDefault() == 1) {
            clearOtherDefaults();
        }
        providerMapper.insert(entity);
        auditService.record(AuditActionEnum.STORAGE_PROVIDER_CREATE, "STORAGE_PROVIDER",
                String.valueOf(entity.getId()), "创建服务商 " + entity.getCode());
        return toVO(entity);
    }

    @Override
    @Transactional
    @Idempotent(keyPrefix = "storage-provider-update")
    @RateLimit(keyPrefix = "storage-provider-write", permits = 20, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public StorageProviderVO update(String id, StorageProviderRequest request) {
        SysStorageProvider entity = require(id);
        StorageProviderTypeEnum type = validate(request);
        entity.setName(request.getName() == null ? "" : request.getName());
        entity.setProviderType(type.getCode());
        entity.setEndpoint(request.getEndpoint());
        entity.setRegion(request.getRegion() == null ? "" : request.getRegion());
        entity.setBucket(request.getBucket());
        entity.setAccessKey(request.getAccessKey());
        // secretKey 空=不修改；非空则加密存储
        if (request.getSecretKey() != null && !request.getSecretKey().isBlank()) {
            entity.setSecretKey(encryptSafely(request.getSecretKey()));
        }
        entity.setPathStyle(normalizePathStyle(request.getPathStyle()));
        entity.setPublicBaseUrl(request.getPublicBaseUrl());
        if (request.getStatus() != null && VALID_STATUS.contains(request.getStatus())) {
            entity.setStatus(request.getStatus());
        }
        if (request.getIsDefault() != null && request.getIsDefault() == 1) {
            clearOtherDefaults();
            entity.setIsDefault(1);
        }
        entity.setDescription(request.getDescription() == null ? "" : request.getDescription());
        providerMapper.updateById(entity);
        // 配置变更后失效客户端缓存
        registry.invalidate(entity.getCode());
        auditService.record(AuditActionEnum.STORAGE_PROVIDER_UPDATE, "STORAGE_PROVIDER",
                String.valueOf(entity.getId()), "更新服务商 " + entity.getCode());
        return toVO(entity);
    }

    @Override
    @RateLimit(keyPrefix = "storage-provider-write", permits = 20, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public void test(String id) {
        SysStorageProvider entity = require(id);
        try {
            storageClient.testConnection(entity.getCode());
        } catch (ObjectStorageException e) {
            String reason = e.getMessage();
            auditService.record(AuditActionEnum.STORAGE_PROVIDER_TEST, "STORAGE_PROVIDER",
                    String.valueOf(entity.getId()), "测试连通失败 " + entity.getCode() + "：" + reason);
            throw new BizException(BlogErrorCodeEnum.PROVIDER_TEST_FAILED,
                    BlogErrorCodeEnum.PROVIDER_TEST_FAILED.getDesc() + "：" + reason);
        }
        auditService.record(AuditActionEnum.STORAGE_PROVIDER_TEST, "STORAGE_PROVIDER",
                String.valueOf(entity.getId()), "测试连通成功 " + entity.getCode());
    }

    @Override
    @Transactional
    @Idempotent(keyPrefix = "storage-provider-default")
    @RateLimit(keyPrefix = "storage-provider-write", permits = 20, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public void setDefault(String id) {
        SysStorageProvider entity = require(id);
        // 停用中的服务商不能设默认
        if (entity.getStatus() != null && entity.getStatus() == 0) {
            throw new BizException(BlogErrorCodeEnum.PROVIDER_DISABLED,
                    "停用中的服务商不能设为默认，请先启用");
        }
        // 旧默认清零
        providerMapper.update(null, new LambdaUpdateWrapper<SysStorageProvider>()
                .set(SysStorageProvider::getIsDefault, 0)
                .eq(SysStorageProvider::getIsDefault, 1));
        entity.setIsDefault(1);
        providerMapper.updateById(entity);
        // 切换默认只影响新上传，缓存无需失效（同一服务商 client 不变）
        auditService.record(AuditActionEnum.STORAGE_PROVIDER_DEFAULT, "STORAGE_PROVIDER",
                String.valueOf(entity.getId()), "设默认服务商 " + entity.getCode());
    }

    @Override
    @Transactional
    @Idempotent(keyPrefix = "storage-provider-status")
    @RateLimit(keyPrefix = "storage-provider-write", permits = 20, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public void updateStatus(String id, Integer status) {
        if (status == null || !VALID_STATUS.contains(status)) {
            throw new BizException(BlogErrorCodeEnum.PROVIDER_CONFIG_INVALID, "status 取值仅允许 0/1");
        }
        SysStorageProvider entity = require(id);
        entity.setStatus(status);
        providerMapper.updateById(entity);
        registry.invalidate(entity.getCode());
        auditService.record(AuditActionEnum.STORAGE_PROVIDER_STATUS, "STORAGE_PROVIDER",
                String.valueOf(entity.getId()),
                (status == 1 ? "启用 " : "停用 ") + entity.getCode());
    }

    @Override
    @Transactional
    @Idempotent(keyPrefix = "storage-provider-delete")
    @RateLimit(keyPrefix = "storage-provider-write", permits = 20, windowSeconds = 60, dimension = RateLimit.Dimension.USER)
    public void delete(String id) {
        SysStorageProvider entity = require(id);
        // 引用文件保护：deleted=0 的 sys_file 引用该 storage_code 才拒绝
        Long refCount = fileMapper.selectCount(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getStorageCode, entity.getCode()));
        if (refCount != null && refCount > 0) {
            throw new BizException(BlogErrorCodeEnum.PROVIDER_IN_USE,
                    BlogErrorCodeEnum.PROVIDER_IN_USE.getDesc()
                            + "（引用文件数=" + refCount + "，建议改用停用）");
        }
        providerMapper.deleteById(entity.getId());
        registry.invalidate(entity.getCode());
        auditService.record(AuditActionEnum.STORAGE_PROVIDER_DELETE, "STORAGE_PROVIDER",
                String.valueOf(entity.getId()), "删除服务商 " + entity.getCode());
    }

    private SysStorageProvider require(String id) {
        Long pid;
        try {
            pid = Long.valueOf(id);
        } catch (Exception e) {
            throw new BizException(BlogErrorCodeEnum.PROVIDER_NOT_FOUND);
        }
        SysStorageProvider entity = providerMapper.selectById(pid);
        if (entity == null || Integer.valueOf(1).equals(entity.getDeleted())) {
            throw new BizException(BlogErrorCodeEnum.PROVIDER_NOT_FOUND);
        }
        return entity;
    }

    private StorageProviderTypeEnum validate(StorageProviderRequest request) {
        StorageProviderTypeEnum type = StorageProviderTypeEnum.fromCode(request.getProviderType());
        if (type == null) {
            throw new BizException(BlogErrorCodeEnum.PROVIDER_CONFIG_INVALID,
                    "providerType 非法：" + request.getProviderType());
        }
        if (type.isRegionRequired() && (request.getRegion() == null || request.getRegion().isBlank())) {
            throw new BizException(BlogErrorCodeEnum.PROVIDER_CONFIG_INVALID,
                    type.getCode() + " 类型 region 必填");
        }
        return type;
    }

    private int normalizePathStyle(Integer pathStyle) {
        if (pathStyle == null) {
            return 1;
        }
        return VALID_PATH_STYLE.contains(pathStyle) ? pathStyle : 1;
    }

    private void clearOtherDefaults() {
        providerMapper.update(null, new LambdaUpdateWrapper<SysStorageProvider>()
                .set(SysStorageProvider::getIsDefault, 0)
                .eq(SysStorageProvider::getIsDefault, 1));
    }

    private StorageProviderVO toVO(SysStorageProvider entity) {
        StorageProviderVO vo = new StorageProviderVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setProviderType(entity.getProviderType());
        vo.setEndpoint(entity.getEndpoint());
        vo.setRegion(entity.getRegion());
        vo.setBucket(entity.getBucket());
        vo.setAccessKey(entity.getAccessKey());
        vo.setSecretKeyMasked(maskSecretKey(entity.getSecretKey()));
        vo.setPathStyle(entity.getPathStyle());
        vo.setPublicBaseUrl(entity.getPublicBaseUrl());
        vo.setStatus(entity.getStatus());
        vo.setIsDefault(entity.getIsDefault());
        vo.setDescription(entity.getDescription());
        if (entity.getCreateTime() != null) {
            vo.setCreateTime(entity.getCreateTime().format(TIME));
        }
        if (entity.getUpdateTime() != null) {
            vo.setUpdateTime(entity.getUpdateTime().format(TIME));
        }
        return vo;
    }

    /** SK 掩码：已配置返回 "****"，空返回 ""；禁止明文回显 */
    private static String maskSecretKey(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            return "";
        }
        return "****";
    }

    /** 加密 SK；主密钥缺失时翻译为业务错误码（而非 500） */
    private String encryptSafely(String plaintext) {
        try {
            return cipher.encrypt(plaintext);
        } catch (ObjectStorageException e) {
            if (e.getReason() == ObjectStorageException.Reason.MASTER_KEY_MISSING) {
                throw new BizException(BlogErrorCodeEnum.PROVIDER_CONFIG_INVALID,
                        "SK 主密钥未配置：" + e.getMessage());
            }
            throw e;
        }
    }
}