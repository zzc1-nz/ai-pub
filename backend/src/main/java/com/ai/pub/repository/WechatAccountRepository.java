package com.ai.pub.repository;

import com.ai.pub.entity.WechatAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WechatAccountRepository extends JpaRepository<WechatAccount, Long> {

    Optional<WechatAccount> findByAppId(String appId);

    List<WechatAccount> findByStatus(Integer status);
}
