package net.lab1024.sa.admin.module.scm.inventory.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 规格转换单明细行：一行 = 「源 SKU 出 N（单位 U₁） → 目标 SKU 入 M（单位 U₂）」。
 *
 * <p><b>两个数量都恒为正</b>：方向由「转出 / 转入」这个动作决定（源一律出、目标一律入），
 * 不由数量正负表达 —— 用正负号表达会让「这一行到底是加还是减」变得需要推理。
 *
 * <p><b>两个单位都由单据显式声明</b>，不从余额推导：折算关系本身含单位
 * （1 箱 = 10 kg），让系统去猜等于把「箱」和「kg」的关系交给启发式。
 * 执行时：
 * <ul>
 *   <li>{@code sourceUnit} 必须等于源 SKU 余额行的记账单位（否则 41059）；</li>
 *   <li>{@code targetUnit} 在目标 SKU 已有余额行时必须等于其单位（否则 41060），
 *       没有余额行时用它**建立**该行（入方向才允许建行）。</li>
 * </ul>
 */
@Data
@TableName(value = "inventory_conversion_item", autoResultMap = true)
public class InventoryConversionItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long conversionId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceSkuId;

    /** 源数量，恒为正。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal sourceQuantity;

    /** 源单位（单据显式声明）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceUnit;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long targetSkuId;

    /** 目标数量，恒为正；与源数量构成折算关系。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal targetQuantity;

    /** 目标单位（单据显式声明）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String targetUnit;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

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
