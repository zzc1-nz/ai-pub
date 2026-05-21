package com.ai.pub.repository;

import com.ai.pub.entity.WechatMaterial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WechatMaterialRepository extends JpaRepository<WechatMaterial, Long> {

    Page<WechatMaterial> findByAccountId(Long accountId, Pageable pageable);

    Page<WechatMaterial> findByAccountIdAndType(Long accountId, String type, Pageable pageable);

    List<WechatMaterial> findByAccountIdAndType(Long accountId, String type);
}
