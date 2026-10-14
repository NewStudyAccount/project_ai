package com.qjj.blog.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qjj.blog.common.enums.BlogErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.framework.idempotent.Idempotent;
import com.qjj.blog.service.entity.BlogSiteConfig;
import com.qjj.blog.service.enums.AuditActionEnum;
import com.qjj.blog.service.mapper.BlogSiteConfigMapper;
import com.qjj.blog.service.vo.SiteConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点配置（config_key 多行）。
 */
@Service
@RequiredArgsConstructor
public class SiteConfigService {

    public static final String KEY_TITLE = "site.title";
    public static final String KEY_DESC = "site.description";
    public static final String KEY_COPYRIGHT = "site.copyright";
    public static final String KEY_ABOUT = "site.about";

    private final BlogSiteConfigMapper configMapper;
    private final IdGenerator idGenerator;
    private final AuditService auditService;

    public SiteConfigVO get() {
        List<BlogSiteConfig> list = configMapper.selectList(new LambdaQueryWrapper<BlogSiteConfig>()
                .in(BlogSiteConfig::getConfigKey, KEY_TITLE, KEY_DESC, KEY_COPYRIGHT, KEY_ABOUT));
        Map<String, String> map = new HashMap<>();
        for (BlogSiteConfig item : list) {
            map.put(item.getConfigKey(), item.getConfigValue());
        }
        SiteConfigVO vo = new SiteConfigVO();
        vo.setTitle(map.getOrDefault(KEY_TITLE, ""));
        vo.setDescription(map.getOrDefault(KEY_DESC, ""));
        vo.setCopyright(map.getOrDefault(KEY_COPYRIGHT, ""));
        vo.setAbout(map.getOrDefault(KEY_ABOUT, ""));
        return vo;
    }

    public Map<String, String> listAll() {
        List<BlogSiteConfig> list = configMapper.selectList(null);
        Map<String, String> map = new HashMap<>();
        for (BlogSiteConfig item : list) {
            map.put(item.getConfigKey(), item.getConfigValue());
        }
        return map;
    }

    @Transactional
    @Idempotent(keyPrefix = "site-config-update")
    public SiteConfigVO save(Map<String, String> body) {
        if (body == null || body.isEmpty()) {
            throw new BizException(BlogErrorCodeEnum.SITE_CONFIG_KEY_INVALID);
        }
        for (Map.Entry<String, String> entry : body.entrySet()) {
            upsert(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        auditService.record(AuditActionEnum.SITE_CONFIG_UPDATE, "SITE_CONFIG", "0", "批量更新站点配置");
        return get();
    }

    private void upsert(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new BizException(BlogErrorCodeEnum.SITE_CONFIG_KEY_INVALID);
        }
        BlogSiteConfig existing = configMapper.selectOne(new LambdaQueryWrapper<BlogSiteConfig>()
                .eq(BlogSiteConfig::getConfigKey, key)
                .last("limit 1"));
        if (existing == null) {
            BlogSiteConfig entity = new BlogSiteConfig();
            entity.setId(idGenerator.nextId());
            entity.setConfigKey(key);
            entity.setConfigValue(value);
            entity.setRemark("");
            configMapper.insert(entity);
        } else {
            existing.setConfigValue(value);
            configMapper.updateById(existing);
        }
    }
}
