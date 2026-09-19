package net.lab1024.sa.admin.module.scm.inventory.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 规格转换单（整件拆零 / 组合拆分）。
 *
 * <p><b>跨 SKU、同仓库</b>：一次转换把源 SKU 的库存转成目标 SKU 的库存，
 * 两者在同一个仓库。跨仓搬运是**调拨**（V31），不是转换 —— 职责必须分清，
 * 否则会出现「用转换单搬货」这种绕过调拨在途语义的用法。
 *
 * <p><b>Q13 不受影响</b>：源与目标是两个不同的 {@code (warehouse_id, sku_id)}，
 * 各自仍然只锁一个记账单位。此前文档里「单位转换会推翻 Q13」的表述已更正。
 *
 * <p><b>审批状态机与报损报溢同构</b>：转换会把两个 SKU 的余额同时改掉，
 * 且折算关系是人工声明的，没有审批等于录单人可以单方面决定「一箱等于多少 kg」。
 */
@Data
@TableName(value = "inventory_conversion", autoResultMap = true)
public class InventoryConversionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 转换单号（业务唯一，软删范围内唯一）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String conversionNo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;

    /** {@code ScmInventoryConversionTypeEnum}：SPLIT / COMBINE。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String convertType;

    /** {@code ScmInventoryConversionStatusEnum}。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reason;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    /** 审核时刻；仅已审核非空（DB 双侧 CHECK 保证待审核时为空）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime auditedAt;

    /** 审核人；仅已审核非空。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String auditor;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String auditOpinion;

    /** 乐观锁版本号；审批时用于防「读后被改」。 */
    @Version
    private Integer version = 0;

    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime createdAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime updatedAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String createdBy;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String updatedBy;
}
