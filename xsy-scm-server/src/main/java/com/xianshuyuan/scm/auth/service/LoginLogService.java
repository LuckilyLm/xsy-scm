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
                        """, userId, bounded(username, 64, ""), result, bounded(reason, 64, null),
                bounded(ip, 64, null), bounded(userAgent, 500, null));
    }

    private String bounded(String value, int maxLength, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String clean = value.replaceAll("[\\p{Cntrl}]", " ").strip();
        int length = clean.codePointCount(0, clean.length());
        return length <= maxLength ? clean : clean.substring(0, clean.offsetByCodePoints(0, maxLength));
    }
}
