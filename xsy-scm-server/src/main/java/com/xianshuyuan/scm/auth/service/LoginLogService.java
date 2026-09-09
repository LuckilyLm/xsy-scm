package com.xianshuyuan.scm.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginLogService {
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(Long userId, String username, String result, String reason, String ip, String userAgent) {
        jdbc.update("""
                INSERT INTO sys_login_log
                    (user_id, username_snapshot, result, failure_reason_code, ip, user_agent)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, usernameSnapshot(username), result, nullable(reason), nullable(ip), nullable(userAgent));
    }

    private String usernameSnapshot(String value) {
        return value == null ? "" : value.strip();
    }

    private String nullable(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip();
    }
}
