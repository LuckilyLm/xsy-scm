package net.lab1024.sa.admin.module.scm.supplier;

import net.lab1024.sa.admin.module.scm.common.ScmW2PgITBase;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierAddForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierQueryForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierSkuItemForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierSkuReplaceForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierStatusForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.vo.SupplierDetailVO;
import net.lab1024.sa.admin.module.scm.supplier.domain.vo.SupplierOptionVO;
import net.lab1024.sa.admin.module.scm.supplier.domain.vo.SupplierVO;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierQueryService;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierService;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierSkuService;
import net.lab1024.sa.base.common.domain.PageParam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 供应商读路径在真实 PostgreSQL 上的行为（T11）。
 *
 * <p>关键区分（legacy S11）：<b>管理列表返回全部状态，下拉只返回 ENABLED</b>。
 * 两者混用会让停用供应商继续出现在新建商品的选项里。
 */
@DisplayName("供应商：读路径与 skuCount（PG IT）")
class SupplierQueryServiceIT extends ScmW2PgITBase {

    @Autowired
    private SupplierService service;

    @Autowired
    private SupplierQueryService queryService;

    @Autowired
    private SupplierSkuService skuService;

    private SupplierAddForm form(String suffix) {
        SupplierAddForm form = new SupplierAddForm();
        form.setSupplierCode(prefix + "-" + suffix);
        form.setName("供应商" + suffix);
        return form;
    }

    private SupplierQueryForm queryForm() {
        SupplierQueryForm form = new SupplierQueryForm();
        form.setPageNum(1L);
        form.setPageSize(50L);
        form.setKeyword(prefix);
        return form;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "CONTACT-NAME", "13900002222"})
    @DisplayName("关键字覆盖编码 / 名称 / 联系人 / 联系电话")
    void keywordCoversCodeNameContactAndPhone(String keyword) {
        SupplierAddForm form = form("K1");
        form.setContactName(prefix + "CONTACT-NAME");
        form.setContactPhone("13900002222");
        Long id = service.add(form);

        SupplierQueryForm query = queryForm();
        query.setKeyword(keyword.isEmpty() ? prefix : keyword);
        assertThat(queryService.query(query).getList()).extracting(SupplierVO::getSupplierId).contains(id);
    }

    @Test
    @DisplayName("管理列表返回全部状态，下拉只返回 ENABLED（S11）")
    void listReturnsAllStatusesWhileOptionsAreEnabledOnly() {
        Long enabled = service.add(form("e"));
        Long disabled = service.add(form("d"));

        SupplierStatusForm status = new SupplierStatusForm();
        status.setSupplierId(disabled);
        status.setVersion(0);
        status.setStatus("DISABLED");
        service.updateStatus(status);

        SupplierQueryForm byStatus = queryForm();
        byStatus.setStatus("DISABLED");
        assertThat(queryService.query(byStatus).getList()).extracting(SupplierVO::getSupplierId)
                .containsExactly(disabled);

        assertThat(queryService.query(queryForm()).getList()).extracting(SupplierVO::getSupplierId)
                .contains(enabled, disabled);

        assertThat(queryService.optionList()).extracting(SupplierOptionVO::getSupplierId)
                .contains(enabled).doesNotContain(disabled);
    }

    @Test
    @DisplayName("列表与详情的 skuCount 反映活动关联数（软删不计入）")
    void skuCountCountsActiveRelationsOnly() {
        Long supplierId = service.add(form("c"));
        Long firstSku = newOnShelfSku("C1");
        Long secondSku = newOnShelfSku("C2");

        SupplierSkuReplaceForm replace = new SupplierSkuReplaceForm();
        replace.setSupplierId(supplierId);
        SupplierSkuItemForm first = new SupplierSkuItemForm();
        first.setSkuId(firstSku);
        first.setPurchaseUnit("kg");
        SupplierSkuItemForm second = new SupplierSkuItemForm();
        second.setSkuId(secondSku);
        second.setPurchaseUnit("kg");
        replace.setItems(List.of(first, second));
        skuService.replace(replace);

        SupplierVO row = queryService.query(queryForm()).getList().stream()
                .filter(vo -> vo.getSupplierId().equals(supplierId)).findFirst().orElseThrow();
        assertThat(row.getSkuCount()).isEqualTo(2L);
        assertThat(queryService.detail(supplierId).getSkuCount()).isEqualTo(2L);

        replace.setItems(List.of(first));
        skuService.replace(replace);

        SupplierDetailVO detail = queryService.detail(supplierId);
        assertThat(detail.getSkuCount()).as("软删的关联不再计入").isEqualTo(1L);
        assertThat(detail.getSupplierCode()).isEqualTo((prefix + "-c").toUpperCase(Locale.ROOT));
    }

    @Test
    @DisplayName("详情不存在 → 40440")
    void detailMissingIs40440() {
        expectCode(() -> queryService.detail(-1L), 40440);
    }

    @Test
    @DisplayName("排序白名单：合法列通过，未知列 40000")
    void sortWhitelistIsEnforced() {
        Long id = service.add(form("s"));

        PageParam.SortItem legal = new PageParam.SortItem();
        legal.setIsAsc(false);
        legal.setColumn("updated_at");
        SupplierQueryForm good = queryForm();
        good.setSortItemList(List.of(legal));
        assertThat(queryService.query(good).getList()).extracting(SupplierVO::getSupplierId).contains(id);

        PageParam.SortItem illegal = new PageParam.SortItem();
        illegal.setIsAsc(true);
        illegal.setColumn("remark");
        SupplierQueryForm bad = queryForm();
        bad.setSortItemList(List.of(illegal));
        expectCode(() -> queryService.query(bad), 40000);
    }
}
