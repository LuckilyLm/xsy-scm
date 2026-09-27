package com.xsy.scm.purchase.manager;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.error.ScmErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.purchase.domain.form.PurchaseOrderAddForm;
import com.xsy.scm.supplier.domain.entity.SupplierEntity;
import com.xsy.scm.supplier.domain.entity.SupplierSkuEntity;
import com.xsy.scm.supplier.service.SupplierService;
import com.xsy.scm.supplier.service.SupplierSkuService;
import com.xsy.scm.warehouse.domain.entity.WarehouseEntity;
import com.xsy.scm.warehouse.service.WarehouseService;
import org.springframework.stereotype.Component;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_ALLOCATION_DUPLICATE;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_VERSION_REQUIRED;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_DUPLICATE_SKU;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_EMPTY;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_PRICE_INVALID;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_QUANTITY_INVALID;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_SUPPLIER_DISABLED;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_SUPPLIER_SKU_DISABLED;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_WAREHOUSE_DISABLED;

/**
 * 采购单输入与主数据引用校验。
 *
 * <p>
 * 标量和集合规则在写入前统一校验；供应商、仓库与可采购 SKU 通过各自的域服务读取。 数量与单价使用严格四位小数格式，并映射到采购域错误码。
 *
 * <p>
 * {@code SupplierSkuService.requireEnabledForPurchasing} 是可采购性唯一入口；本类将供应商域错误 映射为采购域错误，避免采购调用方依赖供应商错误码。
 */
@Component
@RequiredArgsConstructor
public class PurchaseOrderValidator {

    private final SupplierService supplierService;

    private final SupplierSkuService supplierSkuService;

    private final WarehouseService warehouseService;

    // ------------------------------------------------------------------
    // 静态纯函数
    // ------------------------------------------------------------------

    /**
     * 去首尾空白；空白视作 {@code null}。
     */
    public static String trim(String inputText) {
        return StringUtils.isBlank(inputText) ? null : StringUtils.trimToNull(inputText);
    }

    /**
     * 必填文本（原因类字段），缺失 → 传入的错误码。
     */
    public static void reason(String reasonText, ScmErrorCode code) {
        if (trim(reasonText) == null) {
            throw new ScmBusinessException(code);
        }
    }

    /**
     * 解析 4 位定点字符串。
     *
     * @param positive
     *            {@code true} = 数量（必须 &gt; 0，否则 40080）； {@code false} = 单价（必须 &ge; 0，否则 40081）
     */
    public static BigDecimal decimal(String decimalText, boolean positive) {
        ScmErrorCode code = positive ? PURCHASE_QUANTITY_INVALID : PURCHASE_PRICE_INVALID;
        if (decimalText == null || !decimalText.matches("[0-9]{1,14}\\.[0-9]{4}")) {
            throw new ScmBusinessException(code);
        }
        BigDecimal result = new BigDecimal(decimalText);
        if (positive && result.signum() <= 0) {
            throw new ScmBusinessException(code);
        }
        return result;
    }

    /**
     * 新建 / 编辑草稿单的表单规则（不涉及主数据）。
     *
     * <p>
     * 覆盖：至少一行（40089）· 每行 SKU 非空且不重复（40997）· 数量 / 单价形态（40080 / 40081）· 每条分配必须带 `demandVersion`（40091）· 同一行内 `demandId`
     * 不重复（40090）。
     */
    public static void draft(PurchaseOrderAddForm form) {
        List<PurchaseOrderAddForm.Item> items = form.getItems();
        if (items == null || items.isEmpty()) {
            throw new ScmBusinessException(PURCHASE_ORDER_ITEM_EMPTY);
        }
        Set<Long> skus = new HashSet<>();
        for (PurchaseOrderAddForm.Item item : items) {
            if (item.getSkuId() == null || !skus.add(item.getSkuId())) {
                throw new ScmBusinessException(PURCHASE_ORDER_ITEM_DUPLICATE_SKU);
            }
            decimal(item.getQuantity(), true);
            decimal(item.getPrice(), false);
            if (item.getAllocations() == null) {
                continue;
            }
            Set<Long> demands = new HashSet<>();
            for (PurchaseOrderAddForm.Allocation allocation : item.getAllocations()) {
                if (allocation.getDemandId() == null || !demands.add(allocation.getDemandId())) {
                    throw new ScmBusinessException(PURCHASE_DEMAND_ALLOCATION_DUPLICATE);
                }
                if (allocation.getDemandVersion() == null) {
                    throw new ScmBusinessException(PURCHASE_DEMAND_VERSION_REQUIRED);
                }
                decimal(allocation.getQuantity(), true);
            }
        }
    }

    // ------------------------------------------------------------------
    // 引用校验（需要主数据）
    // ------------------------------------------------------------------

    /**
     * 供应商必须存在且启用，否则 40986。
     *
     * <p>
     * 存在性走 的 {@code require}（40440），**启用判定用 的码** —— 因为 「供应商已停用，不能用于新采购单」是采购侧规则，不是供应商域自身的不变量。
     */
    public SupplierEntity requireEnabledSupplier(Long supplierId) {
        SupplierEntity supplier = supplierService.require(supplierId);
        if (!ScmEnableStatusEnum.ENABLED.name().equals(supplier.getStatus())) {
            throw new ScmBusinessException(PURCHASE_SUPPLIER_DISABLED);
        }
        return supplier;
    }

    /**
     * 仓库必须存在（40485）且启用，否则 40987。
     */
    public WarehouseEntity requireEnabledWarehouse(Long warehouseId) {
        WarehouseEntity warehouse = warehouseService.require(warehouseId);
        if (!ScmEnableStatusEnum.ENABLED.name().equals(warehouse.getStatus())) {
            throw new ScmBusinessException(PURCHASE_WAREHOUSE_DISABLED);
        }
        return warehouse;
    }

    /**
     * 该 SKU 必须能由该供应商供货（的唯一判定入口）。
     *
     * <p>
     * 返回的 {@code supplier_sku} 提供 需要的 `purchase_unit`（→ `purchase_order_item.purchase_unit_snapshot`）与
     * `reference_price`（只作建议值）。 的失败码在这里被翻译成 40992。
     */
    public SupplierSkuEntity requirePurchasableSku(Long supplierId, Long skuId) {
        try {
            return supplierSkuService.requireEnabledForPurchasing(supplierId, skuId);
        } catch (ScmBusinessException e) {
            // 供应商本身的问题已在上一步以 40986 拦下；走到这里只可能是 SKU 关系不可采购
            throw new ScmBusinessException(PURCHASE_SUPPLIER_SKU_DISABLED);
        }
    }
}
