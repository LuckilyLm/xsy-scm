package com.xianshuyuan.scm.auth.service;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

@Service
public class UserSessionService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public UserSessionService(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public void invalidateAll(String username) {
        sessionRepository.findByPrincipalName(username).keySet().forEach(sessionRepository::deleteById);
    }
}
