package com.xsy.scm.device.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 电子秤设备绑定。配对码与设备凭证只保存摘要，明文仅在签发时返回一次。
 *
 * <p>
 * 设备身份只证明「哪台秤」，不携带订单、库存或财务权限；业务写入还需要员工授权会话。
 */
@Data
@TableName(value = "xsy_scale_device")
public class ScaleDeviceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 外部设备编号，全局唯一；解绑不释放，防止历史读数被另一台秤继承。 */
    private String deviceCode;

    private String deviceName;

    private String deviceModel;

    /** 绑定仓库；设备可见任务不超出该仓库。 */
    private Long warehouseId;

    private String stationCode;

    /** {@code PENDING_ACTIVATION} / {@code ACTIVE} / {@code SUSPENDED} / {@code REVOKED}。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;

    private String protocolType;

    private String agentVersion;

    /** 一次性配对码摘要；激活成功后清空，不能重放。 */
    private String pairingCodeHash;

    private OffsetDateTime pairingExpiresAt;

    /** 设备凭证摘要；轮换即替换，旧值立即失效。 */
    private String credentialHash;

    private OffsetDateTime credentialExpiresAt;

    private OffsetDateTime lastSeenAt;

    private OffsetDateTime lastReadingAt;

    /** 设备自报待同步数，仅运维观测，不能据此推断云端无遗漏。 */
    private Integer queueDepth;

    private Boolean scaleConnected;

    private Boolean printerConnected;

    private OffsetDateTime revokedAt;

    private String revokedBy;

    @Version
    private Integer version = 0;

    /** 逻辑删除只用于误登记的设备；正常解绑走状态迁移，历史读数仍要指回原设备。 */
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private String createdBy;

    private String updatedBy;
}
