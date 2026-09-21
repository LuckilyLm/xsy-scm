package net.lab1024.sa.admin.module.scm.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 调拨单明细行（独立 VO，供 mapper 与命令侧复用）。
 *
 * <p>与 {@link InventoryTransferVO.Item} 字段一致但**不复用同一个类**，
 * 理由与其它库存单据明细相同：头内嵌明细是「详情的组成部分」，
 * 独立投影是「一行的视图」，两者演进理由不同。
 */
@Data
public class InventoryTransferItemVO {

    private Long id;

    private Long transferId;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String productName;

    private Map<String, String> specValues;

    /**
     * 调拨数量，恒为正。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal quantity;

    /**
     * 发出时写入的源仓记账单位快照；草稿态为空。
     */
    private String unitSnapshot;

    private String remark;
}
