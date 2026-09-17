package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.constant.ScmEnableStatusEnum;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderAddForm;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderValidator;
import net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierEntity;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierSkuEntity;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierService;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierSkuService;
import net.lab1024.sa.admin.module.scm.warehouse.domain.entity.WarehouseEntity;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 采购单校验器契约测试（W5 Target Design §11.1，8 例；无 DB，Mockito）。
 *
 * <p>覆盖两层：
 * <ul>
 *   <li><b>静态纯函数</b>（{@code trim} / {@code reason} / {@code decimal} / {@code draft}）——
 *       锁定 §7.2 的「4 位定点字符串」入站形态与 40080 / 40081 / 40089 / 40090 / 40091 / 40997；</li>
 *   <li><b>引用校验</b>（供应商 / 仓库 / supplier_sku）—— 锁定 40986 / 40987，以及
 *       **错误码防腐层**：W2 的失败码在采购边界被翻译成 40992。</li>
 * </ul>
 */
class PurchaseOrderValidatorTest {

    private final SupplierService supplierService = mock(SupplierService.class);

    private final SupplierSkuService supplierSkuService = mock(SupplierSkuService.class);

    private final WarehouseService warehouseService = mock(WarehouseService.class);

    private final PurchaseOrderValidator validator =
            new PurchaseOrderValidator(supplierService, supplierSkuService, warehouseService);

    private static int codeOf(Throwable t) {
        return ((ScmBusinessException) t).getErrorCode().getCode();
    }

    private static PurchaseOrderAddForm baseForm() {
        PurchaseOrderAddForm form = new PurchaseOrderAddForm();
        form.setSupplierId(1L);
        form.setWarehouseId(1L);
        form.setItems(List.of(item(100L, "3.0000", "2.5000", null)));
        return form;
    }

    private static PurchaseOrderAddForm.Item item(Long skuId, String quantity, String price,
                                                  List<PurchaseOrderAddForm.Allocation> allocations) {
        PurchaseOrderAddForm.Item row = new PurchaseOrderAddForm.Item();
        row.setSkuId(skuId);
        row.setQuantity(quantity);
        row.setPrice(price);
        row.setAllocations(allocations);
        return row;
    }

    private static PurchaseOrderAddForm.Allocation allocation(Long demandId, String quantity, Integer version) {
        PurchaseOrderAddForm.Allocation row = new PurchaseOrderAddForm.Allocation();
        row.setDemandId(demandId);
        row.setQuantity(quantity);
        row.setDemandVersion(version);
        return row;
    }

    // ------------------------------------------------------------------
    // 归一化与必填
    // ------------------------------------------------------------------

    @Test
    @DisplayName("trim：空白视作 null（以便真正清空列）")
    void trimNormalizesBlankToNull() {
        assertThat(PurchaseOrderValidator.trim("  采购备注  ")).isEqualTo("采购备注");
        assertThat(PurchaseOrderValidator.trim("")).isNull();
        assertThat(PurchaseOrderValidator.trim("   ")).isNull();
        assertThat(PurchaseOrderValidator.trim(null)).isNull();
    }

    @Test
    @DisplayName("reason：原因类字段空白 → 传入的专用错误码（不是通用 40000）")
    void reasonUsesDedicatedCode() {
        assertThatThrownBy(() -> PurchaseOrderValidator.reason(
                "  ", PurchaseErrorCode.PURCHASE_CANCEL_REASON_REQUIRED))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40086));
        assertThatThrownBy(() -> PurchaseOrderValidator.reason(
                null, PurchaseErrorCode.PURCHASE_SHORT_CLOSE_REASON_REQUIRED))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40087));
        assertThatCode(() -> PurchaseOrderValidator.reason(
                "供应商临时缺货", PurchaseErrorCode.PURCHASE_CANCEL_REASON_REQUIRED))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // 4 位定点字符串形态（§7.2）
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"1", "1.000", "1.00000", "-1.0000", "0.0000", "1e4", "100000000000000.0000", "1.0000 "})
    @DisplayName("数量：非「[0-9]{1,14}.0000」形态或非正 → 40080")
    void invalidQuantityRejected(String value) {
        assertThatThrownBy(() -> PurchaseOrderValidator.decimal(value, true))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40080));
    }

    @Test
    @DisplayName("单价：允许 0.0000（免费赠品），负数与非 4 位小数 → 40081")
    void priceRules() {
        assertThat(PurchaseOrderValidator.decimal("0.0000", false)).isEqualByComparingTo("0.0000");
        assertThat(PurchaseOrderValidator.decimal("99999999999999.9999", false))
                .isEqualByComparingTo("99999999999999.9999");
        assertThatThrownBy(() -> PurchaseOrderValidator.decimal("-0.0001", false))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40081));
        assertThatThrownBy(() -> PurchaseOrderValidator.decimal("2.50", false))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40081));
    }

    // ------------------------------------------------------------------
    // draft：行与分配的表单规则
    // ------------------------------------------------------------------

    @Test
    @DisplayName("draft：至少一行（40089）；SKU 非空且不重复（40997）")
    void draftRequiresNonEmptyDistinctItems() {
        PurchaseOrderAddForm form = baseForm();
        assertThatCode(() -> PurchaseOrderValidator.draft(form)).doesNotThrowAnyException();

        form.setItems(List.of());
        assertThatThrownBy(() -> PurchaseOrderValidator.draft(form))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40089));

        form.setItems(null);
        assertThatThrownBy(() -> PurchaseOrderValidator.draft(form))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40089));

        form.setItems(List.of(item(100L, "1.0000", "1.0000", null), item(100L, "1.0000", "1.0000", null)));
        assertThatThrownBy(() -> PurchaseOrderValidator.draft(form))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40997));

        form.setItems(List.of(item(null, "1.0000", "1.0000", null)));
        assertThatThrownBy(() -> PurchaseOrderValidator.draft(form))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40997));
    }

    @Test
    @DisplayName("draft：同一行重复关联同一需求 → 40090；缺 demandVersion → 40091；分配数量非法 → 40080")
    void draftAllocationRules() {
        PurchaseOrderAddForm form = baseForm();
        form.setItems(List.of(item(100L, "3.0000", "2.5000",
                List.of(allocation(101L, "1.0000", 0)))));
        assertThatCode(() -> PurchaseOrderValidator.draft(form)).doesNotThrowAnyException();

        form.setItems(List.of(item(100L, "3.0000", "2.5000",
                List.of(allocation(101L, "1.0000", 0), allocation(101L, "2.0000", 0)))));
        assertThatThrownBy(() -> PurchaseOrderValidator.draft(form))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40090));

        form.setItems(List.of(item(100L, "3.0000", "2.5000",
                List.of(allocation(101L, "1.0000", null)))));
        assertThatThrownBy(() -> PurchaseOrderValidator.draft(form))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40091));

        form.setItems(List.of(item(100L, "3.0000", "2.5000",
                List.of(allocation(101L, "0.0000", 0)))));
        assertThatThrownBy(() -> PurchaseOrderValidator.draft(form))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40080));
    }

    @Test
    @DisplayName("draft：无分配的行合法（允许先建单后分配）")
    void draftAllowsItemWithoutAllocation() {
        PurchaseOrderAddForm form = baseForm();
        form.setItems(List.of(item(100L, "3.0000", "2.5000", null),
                item(200L, "1.0000", "0.0000", List.of())));
        assertThatCode(() -> PurchaseOrderValidator.draft(form)).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // 引用校验（需要主数据）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("供应商：不存在走 W2 的 40440；已停用 → 40986（采购侧规则）")
    void supplierMustBeEnabled() {
        SupplierEntity enabled = new SupplierEntity();
        enabled.setStatus(ScmEnableStatusEnum.ENABLED.name());
        when(supplierService.require(1L)).thenReturn(enabled);
        assertThat(validator.requireEnabledSupplier(1L)).isSameAs(enabled);

        SupplierEntity disabled = new SupplierEntity();
        disabled.setStatus(ScmEnableStatusEnum.DISABLED.name());
        when(supplierService.require(2L)).thenReturn(disabled);
        assertThatThrownBy(() -> validator.requireEnabledSupplier(2L))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40986));
    }

    @Test
    @DisplayName("仓库：不存在走 40485；已停用 → 40987")
    void warehouseMustBeEnabled() {
        WarehouseEntity enabled = new WarehouseEntity();
        enabled.setStatus(ScmEnableStatusEnum.ENABLED.name());
        when(warehouseService.require(1L)).thenReturn(enabled);
        assertThat(validator.requireEnabledWarehouse(1L)).isSameAs(enabled);

        WarehouseEntity disabled = new WarehouseEntity();
        disabled.setStatus(ScmEnableStatusEnum.DISABLED.name());
        when(warehouseService.require(2L)).thenReturn(disabled);
        assertThatThrownBy(() -> validator.requireEnabledWarehouse(2L))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40987));
    }

    @Test
    @DisplayName("防腐层：W2 的 requireEnabledForPurchasing 失败码被翻译成 40992（W5 只暴露自己的码）")
    void w2FailureIsTranslatedToPurchaseCode() {
        SupplierSkuEntity sku = new SupplierSkuEntity();
        sku.setPurchaseUnit("箱");
        when(supplierSkuService.requireEnabledForPurchasing(1L, 100L)).thenReturn(sku);
        assertThat(validator.requirePurchasableSku(1L, 100L).getPurchaseUnit()).isEqualTo("箱");

        // W2 侧的真实失败码（40442 / 40940 / 40942）对采购域调用方不可见
        when(supplierSkuService.requireEnabledForPurchasing(1L, 200L))
                .thenThrow(new ScmBusinessException(SupplierErrorCode.SUPPLIER_DISABLED));
        assertThatThrownBy(() -> validator.requirePurchasableSku(1L, 200L))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40992));
        when(supplierSkuService.requireEnabledForPurchasing(1L, 300L))
                .thenThrow(new ScmBusinessException(SupplierErrorCode.SUPPLIER_SKU_NOT_FOUND));
        assertThatThrownBy(() -> validator.requirePurchasableSku(1L, 300L))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40992));
    }
}
