package com.ai.pub.controller;

import com.ai.pub.common.Result;
import com.ai.pub.dto.PageQuery;
import com.ai.pub.dto.PageResponse;
import com.ai.pub.entity.WechatMaterial;
import com.ai.pub.service.WechatApiService;
import com.ai.pub.service.WechatMaterialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/api/wechat/materials")
public class WechatMaterialController {

    private static final Logger log = LoggerFactory.getLogger(WechatMaterialController.class);

    private final WechatMaterialService wechatMaterialService;
    private final WechatApiService wechatApiService;

    public WechatMaterialController(WechatMaterialService wechatMaterialService, WechatApiService wechatApiService) {
        this.wechatMaterialService = wechatMaterialService;
        this.wechatApiService = wechatApiService;
    }

    @GetMapping
    public Result<PageResponse<WechatMaterial>> getPage(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String type,
            PageQuery request) {
        PageResponse<WechatMaterial> page = wechatMaterialService.getPage(accountId != null ? accountId : 1L, type, request);
        return Result.success(page);
    }

    @GetMapping("/account/{accountId}")
    public Result<PageResponse<WechatMaterial>> getPageByAccountId(
            @PathVariable Long accountId,
            @RequestParam(required = false) String type,
            PageQuery request) {
        PageResponse<WechatMaterial> page = wechatMaterialService.getPage(accountId, type, request);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result<WechatMaterial> getById(@PathVariable Long id) {
        WechatMaterial material = wechatMaterialService.getById(id);
        return Result.success(material);
    }

    @PostMapping("/upload")
    public Result<WechatMaterial> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type,
            @RequestParam(required = false) Long accountId) throws IOException {

        Long targetAccountId = accountId != null ? accountId : 1L;

        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";

        String newFilename = UUID.randomUUID().toString() + extension;
        String uploadDir = "/app/uploads/";
        Path uploadPath = Paths.get(uploadDir);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path filePath = uploadPath.resolve(newFilename);
        Files.copy(file.getInputStream(), filePath);

        String url = "/uploads/" + newFilename;

        WechatMaterial material = new WechatMaterial();
        material.setAccountId(targetAccountId);
        material.setType(type);
        material.setTitle(originalFilename);
        material.setUrl(url);
        material.setMediaId(newFilename);

        // 上传到微信永久素材库
        try {
            String mediaId = wechatApiService.uploadPermanentMaterial(targetAccountId, type, file.getBytes(), originalFilename);
            material.setMediaId(mediaId);
            log.info("素材已同步到微信: accountId={}, mediaId={}", targetAccountId, mediaId);
        } catch (Exception e) {
            log.warn("上传到微信素材库失败（本地已保存）: {}", e.getMessage());
        }

        WechatMaterial created = wechatMaterialService.create(material);
        return Result.success(created);
    }

    @PostMapping
    public Result<WechatMaterial> create(@RequestBody WechatMaterial material) {
        WechatMaterial created = wechatMaterialService.create(material);
        return Result.success(created);
    }

    @PutMapping("/{id}")
    public Result<WechatMaterial> update(@PathVariable Long id, @RequestBody WechatMaterial material) {
        WechatMaterial updated = wechatMaterialService.update(id, material);
        return Result.success(updated);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        wechatMaterialService.delete(id);
        return Result.success();
    }
}
