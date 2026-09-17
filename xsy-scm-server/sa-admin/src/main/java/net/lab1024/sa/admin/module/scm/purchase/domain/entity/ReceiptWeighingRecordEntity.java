package net.lab1024.sa.admin.module.scm.purchase.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

/**
 * 称重记录（W5 Target Design §5.9）。
 *
 * <p>**只追加的审计事实**：没有 `version` / `deleted` / `updated_*` ——
 * 与 A 源自己的 `inventory_movement` 只追加纪律一致，也意味着 MP 不会对它加 `deleted` 过滤。
 *
 * <p>W5 只允许 `source = MANUAL`（G-05 手工录入），由 `ck_receipt_weighing_record_source` 收紧；
 * `DEVICE` 是 W6+ 接入电子秤时的扩展点。`scale_precision` 已删除（A-D4：W5 无设备精度来源）。
 */
@Data @TableName(value="receipt_weighing_record",autoResultMap=true)
public class ReceiptWeighingRecordEntity {
    @TableId(type=IdType.AUTO) private Long id;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long purchaseReceiptItemId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal rawReading;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class) private BigDecimal confirmedReading;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String unit;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String source;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String deviceSessionId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String modificationReason;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime recordedAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String operator;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime createdAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String createdBy;
}
