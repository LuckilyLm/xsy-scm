package com.xsy.scm.admin.module.business.stock.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存操作表单（统一库存业务层入参）
 *
 * <p>所有模块的库存变动（采购入库 / 销售出库 / 退货入库 / 报损 / 报溢 / 盘点调整 / 规格转换）
 * 都必须通过该表单调用 {@code StockOperateService}，禁止各业务模块直接 UPDATE 库存余额。</p>
 *
 * <p>约定：quantity / weight 一律传正数；盘点调整（flowType=6）时 quantity / weight 为「目标绝对值」，
 * 业务层自动计算差异并生成正向或反向流水。</p>
 *
 * @author xsy-scm
 */
@Data
public class StockOperateForm {

    /**
     * 商品 ID
     */
    @NotNull
    @Schema(description = "商品ID")
    private Long productId;

    /**
     * 规格 ID
     */
    @NotNull
    @Schema(description = "规格ID")
    private Long skuId;

    /**
     * 仓库 ID，G-03 单仓库，缺省为 1
     */
    @Schema(description = "仓库ID，单仓库默认1")
    private Long warehouseId;

    /**
     * 变动数量（正数）；盘点调整时为目标绝对值
     */
    @NotNull
    @Schema(description = "变动数量(正数)；盘点调整时为目标绝对值")
    private BigDecimal quantity;

    /**
     * 变动重量 kg（正数）；盘点调整时为目标绝对值
     */
    @NotNull
    @Schema(description = "变动重量kg(正数)；盘点调整时为目标绝对值")
    private BigDecimal weight;

    /**
     * 变动单价（不含税）；入库成本使用，出库 / 盘点可空
     */
    @Schema(description = "变动单价(不含税)，入库成本使用；出库/盘点可空")
    private BigDecimal unitPrice;

    /**
     * 关联业务类型，见 StockBizTypeEnum
     */
    @NotNull
    @Schema(description = "关联业务类型，见 StockBizTypeEnum")
    private Integer bizType;

    /**
     * 关联业务单 ID
     */
    @Schema(description = "关联业务单ID")
    private Long bizId;

    /**
     * 流水类型，见 StockFlowTypeEnum
     */
    @NotNull
    @Schema(description = "流水类型，见 StockFlowTypeEnum")
    private Integer flowType;

    /**
     * 操作人，可空（缺省取当前登录人）
     */
    @Schema(description = "操作人，可空（取当前登录人）")
    private Long operateBy;

    /**
     * 批次 ID，未启用批次可空
     */
    @Schema(description = "批次ID，未启用批次可空")
    private Long batchId;

    /**
     * 流水号，内部生成，调用方无需填写
     */
    @Schema(hidden = true)
    private String flowNo;
}
