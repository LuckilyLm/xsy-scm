package net.lab1024.sa.admin.module.scm.purchase.manager;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmEnableStatusEnum;
import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderAddForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierEntity;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierSkuEntity;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierService;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierSkuService;
import net.lab1024.sa.admin.module.scm.warehouse.domain.entity.WarehouseEntity;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_ALLOCATION_DUPLICATE;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_VERSION_REQUIRED;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_DUPLICATE_SKU;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_EMPTY;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_PRICE_INVALID;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_QUANTITY_INVALID;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_SUPPLIER_DISABLED;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_SUPPLIER_SKU_DISABLED;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_WAREHOUSE_DISABLED;

/**
 * 采购单业务规则（W5 Target Design §4.2 T1 / §7.2 / §7.4）。
 *
 * <p>分两层：
 * <ul>
 *   <li><b>静态纯函数</b>（{@link #trim} / {@link #reason} / {@link #decimal} / {@link #draft}）——
 *       无 Spring、无 DB，可被单测直接调用；</li>
 *   <li><b>引用校验</b>（{@link #requireEnabledSupplier} / {@link #requireEnabledWarehouse} /
 *       {@link #requirePurchasableSku}）—— 需要读主数据，因此注入 W2 / W5 的 Service，
 *       但**不开事务**（manager 层纪律，§2.2）。</li>
 * </ul>
 *
 * <p><b>为什么不用 {@code ScmDecimalStrings} 解析数量/金额</b>：它抛的是通用的
 * {@code VALIDATION_ERROR(40000)}，而 W5 的错误码契约要求数量 → {@code 40080}、
 * 单价 → {@code 40081}（§7.7）。因此这里用与 W4 相同的严格形态
 * {@code [0-9]{1,14}\.[0-9]{4}} —— 恰好 4 位小数，与 §7.2「请求/响应均为 4 位小数字符串」一致。
 *
 * <p><b>错误码的防腐层</b>：{@code SupplierSkuService.requireEnabledForPurchasing} 是 W2 为采购域
 * 预留的**唯一判定入口**（legacy 不变量 R17，禁止旁路直查 {@code supplier_sku} 表），
 * 但它抛的是 W2 的码（40940 / 40442 / 40942）。W5 对外只暴露自己的码，
 * 因此在边界处把 W2 的失败翻译成 {@code PURCHASE_SUPPLIER_SKU_DISABLED(40992)}。
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

    /** 去首尾空白；空白视作 {@code null}。 */
    public static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 必填文本（原因类字段），缺失 → 传入的错误码。 */
    public static void reason(String value, ScmErrorCode code) {
        if (trim(value) == null) {
            throw new ScmBusinessException(code);
        }
    }

    /**
     * 解析 4 位定点字符串。
     *
     * @param positive {@code true} = 数量（必须 &gt; 0，否则 40080）；
     *                 {@code false} = 单价（必须 &ge; 0，否则 40081）
     */
    public static BigDecimal decimal(String value, boolean positive) {
        ScmErrorCode code = positive ? PURCHASE_QUANTITY_INVALID : PURCHASE_PRICE_INVALID;
        if (value == null || !value.matches("[0-9]{1,14}\\.[0-9]{4}")) {
            throw new ScmBusinessException(code);
        }
        BigDecimal result = new BigDecimal(value);
        if (positive && result.signum() <= 0) {
            throw new ScmBusinessException(code);
        }
        return result;
    }

    /**
     * 新建 / 编辑草稿单的表单规则（不涉及主数据）。
     *
     * <p>覆盖：至少一行（40089）· 每行 SKU 非空且不重复（40997）· 数量 / 单价形态（40080 / 40081）·
     * 每条分配必须带 `demandVersion`（40091）· 同一行内 `demandId` 不重复（40090）。
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
     * <p>存在性走 W2 的 {@code require}（40440），**启用判定用 W5 的码** —— 因为
     * 「供应商已停用，不能用于新采购单」是采购侧规则，不是供应商域自身的不变量。
     */
    public SupplierEntity requireEnabledSupplier(Long supplierId) {
        SupplierEntity supplier = supplierService.require(supplierId);
        if (!ScmEnableStatusEnum.ENABLED.name().equals(supplier.getStatus())) {
            throw new ScmBusinessException(PURCHASE_SUPPLIER_DISABLED);
        }
        return supplier;
    }

    /** 仓库必须存在（40485）且启用，否则 40987。 */
    public WarehouseEntity requireEnabledWarehouse(Long warehouseId) {
        WarehouseEntity warehouse = warehouseService.require(warehouseId);
        if (!ScmEnableStatusEnum.ENABLED.name().equals(warehouse.getStatus())) {
            throw new ScmBusinessException(PURCHASE_WAREHOUSE_DISABLED);
        }
        return warehouse;
    }

    /**
     * 该 SKU 必须能由该供应商供货（W2 的唯一判定入口，R17）。
     *
     * <p>返回的 {@code supplier_sku} 提供 W5 需要的 `purchase_unit`（→
     * `purchase_order_item.purchase_unit_snapshot`）与 `reference_price`（只作建议值，K7）。
     * W2 的失败码在这里被翻译成 40992。
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
