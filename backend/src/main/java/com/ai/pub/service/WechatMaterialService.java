package com.ai.pub.service;

import com.ai.pub.dto.PageQuery;
import com.ai.pub.dto.PageResponse;
import com.ai.pub.entity.WechatMaterial;
import com.ai.pub.repository.WechatMaterialRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WechatMaterialService {

    private final WechatMaterialRepository wechatMaterialRepository;

    public WechatMaterialService(WechatMaterialRepository wechatMaterialRepository) {
        this.wechatMaterialRepository = wechatMaterialRepository;
    }

    public PageResponse<WechatMaterial> getPage(Long accountId, String type, PageQuery request) {
        PageRequest pageable = PageRequest.of(
                request.getPageNum() - 1,
                request.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<WechatMaterial> page;
        if (type != null && !type.isEmpty()) {
            page = wechatMaterialRepository.findByAccountIdAndType(accountId, type, pageable);
        } else {
            page = wechatMaterialRepository.findByAccountId(accountId, pageable);
        }

        return PageResponse.of(page.getContent(), page.getTotalElements(), request.getPageNum(), request.getPageSize());
    }

    public WechatMaterial getById(Long id) {
        return wechatMaterialRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("素材不存在"));
    }

    @Transactional
    public WechatMaterial create(WechatMaterial material) {
        return wechatMaterialRepository.save(material);
    }

    @Transactional
    public WechatMaterial update(Long id, WechatMaterial material) {
        WechatMaterial existing = getById(id);

        existing.setType(material.getType());
        existing.setMediaId(material.getMediaId());
        existing.setTitle(material.getTitle());
        existing.setDescription(material.getDescription());
        existing.setUrl(material.getUrl());
        existing.setContent(material.getContent());
        existing.setThumbMediaId(material.getThumbMediaId());
        existing.setAuthor(material.getAuthor());
        existing.setDigest(material.getDigest());
        existing.setShowCoverPic(material.getShowCoverPic());
        existing.setContentSourceUrl(material.getContentSourceUrl());

        return wechatMaterialRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        wechatMaterialRepository.deleteById(id);
    }
}
