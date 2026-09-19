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
 * 调拨单明细行。
 *
 * <p><b>{@code quantity} 恒为正</b>：方向由「发出 / 收货」这个**动作**决定，
 * 不在行上用正负号表达 —— 同一行会先产生一条转出流水、再产生一条转入流水，
 * 用一个正数同时服务两个方向是唯一不会自相矛盾的做法。
 *
 * <p><b>{@code unitSnapshot} 在发出时写入</b>（= 源仓的记账单位）。
 * 收货时用它断言目标仓的记账单位一致（Q13：不做隐式换算），
 * 并在目标仓从未有过该 SKU 时用它建立新余额行的单位。
 */
@Data
@TableName(value = "inventory_transfer_item", autoResultMap = true)
public class InventoryTransferItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long transferId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long skuId;

    /** 调拨数量，恒为正（DB CHECK）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal quantity;

    /** 发出时写入的记账单位快照（源仓单位）；草稿态为空。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unitSnapshot;

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
