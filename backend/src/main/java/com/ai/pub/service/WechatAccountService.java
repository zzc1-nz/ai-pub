package com.ai.pub.service;

import com.ai.pub.dto.PageQuery;
import com.ai.pub.dto.PageResponse;
import com.ai.pub.entity.WechatAccount;
import com.ai.pub.repository.WechatAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WechatAccountService {

    private final WechatAccountRepository wechatAccountRepository;
    private final WechatApiService wechatApiService;

    public WechatAccountService(WechatAccountRepository wechatAccountRepository, WechatApiService wechatApiService) {
        this.wechatAccountRepository = wechatAccountRepository;
        this.wechatApiService = wechatApiService;
    }

    public PageResponse<WechatAccount> getPage(PageQuery request) {
        PageRequest pageable = PageRequest.of(
                request.getPageNum() - 1,
                request.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<WechatAccount> page = wechatAccountRepository.findAll(pageable);

        return PageResponse.of(page.getContent(), page.getTotalElements(), request.getPageNum(), request.getPageSize());
    }

    public WechatAccount getById(Long id) {
        return wechatAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("公众号不存在"));
    }

    public WechatAccount getByAppId(String appId) {
        return wechatAccountRepository.findByAppId(appId)
                .orElseThrow(() -> new RuntimeException("公众号不存在"));
    }

    @Transactional
    public WechatAccount create(WechatAccount account) {
        if (wechatAccountRepository.findByAppId(account.getAppId()).isPresent()) {
            throw new RuntimeException("AppId已存在");
        }
        return wechatAccountRepository.save(account);
    }

    @Transactional
    public WechatAccount update(Long id, WechatAccount account) {
        WechatAccount existing = getById(id);

        existing.setName(account.getName());
        existing.setAppId(account.getAppId());
        existing.setAppSecret(account.getAppSecret());
        existing.setToken(account.getToken());
        existing.setEncodingAesKey(account.getEncodingAesKey());
        existing.setAiEnabled(account.getAiEnabled());
        existing.setDefaultReply(account.getDefaultReply());
        existing.setStatus(account.getStatus());

        return wechatAccountRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        wechatAccountRepository.deleteById(id);
    }

    public boolean testConnection(Long id) {
        WechatAccount account = getById(id);
        try {
            // 尝试获取 access_token 来验证 AppID/AppSecret 是否有效
            wechatApiService.getAccessToken(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
