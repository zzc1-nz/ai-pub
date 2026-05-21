package com.ai.pub.service;

import com.ai.pub.dto.PageQuery;
import com.ai.pub.dto.PageResponse;
import com.ai.pub.entity.SysUser;
import com.ai.pub.repository.SysUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SysUserService {

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;

    public SysUserService(SysUserRepository sysUserRepository, PasswordEncoder passwordEncoder) {
        this.sysUserRepository = sysUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResponse<SysUser> getPage(PageQuery request) {
        PageRequest pageable = PageRequest.of(
                request.getPageNum() - 1,
                request.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<SysUser> page = sysUserRepository.findAll(pageable);

        return PageResponse.of(page.getContent(), page.getTotalElements(), request.getPageNum(), request.getPageSize());
    }

    public SysUser getById(Long id) {
        return sysUserRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    @Transactional
    public SysUser create(SysUser user) {
        if (sysUserRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return sysUserRepository.save(user);
    }

    @Transactional
    public SysUser update(Long id, SysUser user) {
        SysUser existingUser = getById(id);

        if (!existingUser.getUsername().equals(user.getUsername())
                && sysUserRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }

        existingUser.setUsername(user.getUsername());
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        existingUser.setNickname(user.getNickname());
        existingUser.setRole(user.getRole());
        existingUser.setStatus(user.getStatus());

        return sysUserRepository.save(existingUser);
    }

    @Transactional
    public void delete(Long id) {
        sysUserRepository.deleteById(id);
    }
}
