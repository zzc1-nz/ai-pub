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

    /** 按素材类型分组计数（数据库侧聚合，避免把素材全量载入内存） */
    @org.springframework.data.jpa.repository.Query("SELECT m.type, COUNT(m) FROM WechatMaterial m WHERE m.accountId = :accountId GROUP BY m.type")
    List<Object[]> countByTypeGrouped(@org.springframework.data.repository.query.Param("accountId") Long accountId);
}
