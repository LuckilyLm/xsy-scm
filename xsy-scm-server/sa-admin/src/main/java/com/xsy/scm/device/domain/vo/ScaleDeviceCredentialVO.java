package com.xsy.scm.device.domain.vo;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 激活结果：设备凭证明文只在这里返回一次，服务端只保留摘要。
 *
 * <p>
 * 凭证只代表设备身份，不含订单、库存或财务权限；业务写入仍需要员工授权会话。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScaleDeviceCredentialVO {

    private String accessToken;

    private OffsetDateTime expiresAt;
}
