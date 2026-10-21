package com.xsy.scm.device.domain.vo;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 员工操作会话签发结果。令牌明文只返回这一次。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScaleOperatorSessionVO {

    private String sessionToken;

    private OffsetDateTime expiresAt;

    private Long employeeId;
}
