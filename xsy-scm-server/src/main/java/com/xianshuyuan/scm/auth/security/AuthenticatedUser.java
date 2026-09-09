package com.xianshuyuan.scm.auth.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record AuthenticatedUser(
        long userId,
        String username,
        String displayName,
        long authVersion,
        boolean administrator,
        boolean mustChangePassword,
        List<String> roleCodes
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthenticatedUser {
        roleCodes = List.copyOf(roleCodes);
    }
}
