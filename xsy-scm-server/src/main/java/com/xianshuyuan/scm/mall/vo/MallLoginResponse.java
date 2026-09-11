package com.xianshuyuan.scm.mall.vo;

import java.time.OffsetDateTime;

public record MallLoginResponse(String token, OffsetDateTime expiresAt, MallProfileResponse profile) {
}
