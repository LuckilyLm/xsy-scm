package com.xsy.scm.print;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.print.domain.form.ScmPrintActionForm;
import com.xsy.scm.print.domain.form.ScmPrintTemplateForm;
import com.xsy.scm.print.domain.vo.ScmPrintRenderVO;
import com.xsy.scm.print.service.ScmPrintService;
import com.xsy.scm.print.service.ScmPrintTemplateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * ADM-07 单据模板与打印中心的 PostgreSQL 验收（D-16 补的缺口）。
 *
 * <p>守的是清单 §7 里最容易在改造中被悄悄破掉的几条：打印记录必须冻结当时的模板版本、业务模型与版面，
 * 所以「事后改模板」不能回头改写历史重印结果；重印必须重新判**当前**权限，
 * 不能因为「这条记录是我以前打过的」就绕过查看权；打印与重印都不许动采购单本身的状态。
 *
 * <p>金额权限的剔除路径只有 {@code DELIVERY_NOTE} 挂了金额权限码
 * （{@code PURCHASE_ORDER} / {@code SORTING_TICKET} 的 moneyPermission 为 null），
 * 因此那条口径由配送侧用例覆盖，本类不重复造发货链路。
 */
class ScmPrintCenterPgIT extends ScmW5PgITBase {

    private static final String PURCHASE_ORDER_TYPE = "PURCHASE_ORDER";

    @Autowired
    ScmPrintService printService;

    @Autowired
    ScmPrintTemplateService templateService;

    private MockedStatic<StpUtil> permissions;

    @BeforeEach
    void grantPermissions() {
        permissions = mockStatic(StpUtil.class);
        permissions.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);
    }

    @AfterEach
    void releasePermissions() {
        permissions.close();
    }

    private Long newSubmittedPurchaseOrder() {
        Long supplierId = newSupplier("PRINT");
        Long skuId = newOnShelfSku("PRINT-PO");
        linkSupplierSku(supplierId, skuId, DEFAULT_PURCHASE_UNIT);
        var order = createDraftOrder("PRINT-PO", supplierId, skuId, "10.0000", "5.0000");
        return submitOrder(order.getId()).getId();
    }

    /** 幂等键带本用例前缀：类是共享库上的 PG IT，不能用全局唯一的固定键。 */
    private String key(String tag) {
        return prefix + ":print:" + tag;
    }

    private int recordCount(Long orderId) {
        return jdbc.queryForObject("SELECT count(*) FROM scm_print_record"
                        + " WHERE document_type = ? AND business_id = ?",
                Integer.class, PURCHASE_ORDER_TYPE, orderId);
    }

    private Long latestRecordId(Long orderId) {
        return jdbc.queryForObject("SELECT max(id) FROM scm_print_record"
                        + " WHERE document_type = ? AND business_id = ?",
                Long.class, PURCHASE_ORDER_TYPE, orderId);
    }

    @Test
    @DisplayName("打印记录冻结模板版本与版面：事后改版不回写历史重印")
    void printFreezesTemplateVersionAndLayoutAgainstLaterEdits() {
        Long orderId = newSubmittedPurchaseOrder();
        Long templateId = templateService.requireDefault(PURCHASE_ORDER_TYPE).getId();

        ScmPrintActionForm action = new ScmPrintActionForm();
        action.setTemplateId(templateId);

        ScmPrintRenderVO printed = printService.print(PURCHASE_ORDER_TYPE, orderId, action, key("print"));
        assertThat(printed.getTemplateVersion()).isNotNull();
        String frozenTitle = printed.getTitle();
        String frozenPaper = printed.getPaper();
        String frozenFooter = printed.getFooterNote();
        Integer frozenVersion = printed.getTemplateVersion();
        assertThat(frozenTitle).isNotBlank();

        // 改版：只改标题与页脚（纸张白名单只有 A4 / TICKET_80，不改纸张避免掺入无关口径）
        ScmPrintTemplateForm update = new ScmPrintTemplateForm();
        var detail = templateService.detail(templateId);
        update.setId(detail.getId());
        update.setDocumentType(detail.getDocumentType());
        update.setTemplateCode(detail.getTemplateCode());
        update.setTemplateName(detail.getTemplateName());
        update.setDefaultFlag(detail.getDefaultFlag());
        update.setEnabledFlag(detail.getEnabledFlag());
        update.setRemark(detail.getRemark());
        update.setVersion(detail.getVersion());
        var model = detail.getModel();
        model.setTitle("采购单（已改版）");
        model.setFooterNote("改版后的页脚说明");
        update.setModel(model.toMap());
        templateService.update(update);

        ScmPrintRenderVO reprint = printService.reprint(latestRecordId(orderId));
        assertThat(reprint.getTitle()).as("重印必须用记录里冻结的版面，不能被新模板污染")
                .isEqualTo(frozenTitle).isNotEqualTo("采购单（已改版）");
        assertThat(reprint.getFooterNote()).as("页脚同样取冻结快照")
                .isEqualTo(frozenFooter).isNotEqualTo("改版后的页脚说明");
        assertThat(reprint.getPaper()).isEqualTo(frozenPaper);
        assertThat(reprint.getTemplateVersion()).isEqualTo(frozenVersion);
        assertThat(reprint.isFrozen()).isTrue();
        assertThat(reprint.getBusinessId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("打印与重印都不改变采购单状态")
    void printAndReprintNeverTouchBusinessState() {
        Long orderId = newSubmittedPurchaseOrder();
        var before = reloadOrder(orderId);

        ScmPrintActionForm action = new ScmPrintActionForm();
        action.setTemplateId(templateService.requireDefault(PURCHASE_ORDER_TYPE).getId());
        printService.print(PURCHASE_ORDER_TYPE, orderId, action, key("state"));
        var afterPrint = reloadOrder(orderId);
        printService.reprint(latestRecordId(orderId));
        var afterReprint = reloadOrder(orderId);

        assertThat(afterPrint.getStatus()).isEqualTo(before.getStatus());
        assertThat(afterReprint.getStatus()).isEqualTo(before.getStatus());
        assertThat(afterReprint.getVersion()).isEqualTo(before.getVersion());
    }

    @Test
    @DisplayName("重印复判当前查看权：只有打印记录查询权读不出单据内容")
    void reprintRechecksCurrentViewPermission() {
        Long orderId = newSubmittedPurchaseOrder();
        ScmPrintActionForm action = new ScmPrintActionForm();
        action.setTemplateId(templateService.requireDefault(PURCHASE_ORDER_TYPE).getId());
        printService.print(PURCHASE_ORDER_TYPE, orderId, action, key("reprint-perm"));
        Long recordId = latestRecordId(orderId);

        // 先前打印成功过，此刻撤回功能权限：旧记录绝不能成为读内容的后门。
        // 这里刻意用 anyString：要守的性质是「重印必须重新做一次权限校验并把拒绝传出去」，
        // 而不是钉住某个具体权限码字符串（码值归 PurchasePermission 目录管）。
        permissions.when(() -> StpUtil.checkPermission(anyString()))
                .thenThrow(new IllegalStateException("权限已撤回"));

        assertThatThrownBy(() -> printService.reprint(recordId))
                .as("重印必须重新检查当前单据查看权")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("同一幂等键重试只落一条打印记录")
    void retryWithSameIdempotencyKeyDoesNotDuplicateRecord() {
        Long orderId = newSubmittedPurchaseOrder();
        ScmPrintActionForm action = new ScmPrintActionForm();
        action.setTemplateId(templateService.requireDefault(PURCHASE_ORDER_TYPE).getId());

        String idempotencyKey = key("retry");
        printService.print(PURCHASE_ORDER_TYPE, orderId, action, idempotencyKey);
        printService.print(PURCHASE_ORDER_TYPE, orderId, action, idempotencyKey);

        assertThat(recordCount(orderId))
                .as("重试不能重复生成不该重复的业务事实")
                .isEqualTo(1);
    }

    /** 以默认模板为底本造一份合法表单：只改要改的字段，避免掺进与断言无关的版面口径。 */
    private ScmPrintTemplateForm templateForm(String code, String title) {
        var base = templateService.requireDefault(PURCHASE_ORDER_TYPE);
        var model = templateService.detail(base.getId()).getModel();
        model.setTitle(title);
        ScmPrintTemplateForm form = new ScmPrintTemplateForm();
        form.setDocumentType(PURCHASE_ORDER_TYPE);
        form.setTemplateCode(code);
        form.setTemplateName(code);
        form.setModel(model.toMap());
        return form;
    }

    private int defaultCount() {
        return jdbc.queryForObject("SELECT count(*) FROM scm_print_template"
                        + " WHERE document_type = ? AND default_flag = TRUE AND deleted = FALSE",
                Integer.class, PURCHASE_ORDER_TYPE);
    }

    @Test
    @DisplayName("模板编码唯一：重复编码被拒")
    void createRejectsDuplicatedTemplateCode() {
        var existing = templateService.requireDefault(PURCHASE_ORDER_TYPE);
        ScmPrintTemplateForm duplicate = templateForm(existing.getTemplateCode(), "重复编码模板");

        expectCode(() -> templateService.create(duplicate), 41302);
    }

    @Test
    @DisplayName("非白名单字段拒绝：明细列只能用该单据类型登记的字段")
    void createRejectsFieldOutsideCatalog() {
        ScmPrintTemplateForm form = templateForm(key("field"), "越界字段模板");
        var model = templateService.detail(templateService.requireDefault(PURCHASE_ORDER_TYPE).getId()).getModel();
        model.getColumns().add("internalCostPrice");
        form.setModel(model.toMap());

        expectCode(() -> templateService.create(form), 41306);
    }

    @Test
    @DisplayName("同一单据类型只允许一个默认模板")
    void creatingNewDefaultDemotesTheOldOne() {
        assertThat(defaultCount()).as("种子数据里每类只有一个默认模板").isEqualTo(1);

        ScmPrintTemplateForm form = templateForm(key("default"), "新的采购单默认模板");
        form.setDefaultFlag(Boolean.TRUE);
        templateService.create(form);

        assertThat(defaultCount()).as("新的成为默认后，旧默认必须被降级，不能同时存在两个")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("已被打印记录引用的模板不许删除")
    void deleteRejectsTemplateInUse() {
        Long orderId = newSubmittedPurchaseOrder();
        Long templateId = templateService.requireDefault(PURCHASE_ORDER_TYPE).getId();
        ScmPrintActionForm action = new ScmPrintActionForm();
        action.setTemplateId(templateId);
        printService.print(PURCHASE_ORDER_TYPE, orderId, action, key("in-use"));

        var template = templateService.detail(templateId);
        expectCode(() -> templateService.delete(templateId, template.getVersion()), 41303);
    }

    @Test
    @DisplayName("模板编辑受乐观锁保护：旧版本号改不动已被更新的模板")
    void updateRejectsStaleVersion() {
        var detail = templateService.detail(templateService.requireDefault(PURCHASE_ORDER_TYPE).getId());

        ScmPrintTemplateForm first = templateForm(detail.getTemplateCode(), "第一次改名");
        first.setId(detail.getId());
        first.setVersion(detail.getVersion());
        first.setDocumentType(detail.getDocumentType());
        first.setTemplateName("第一次改名");
        var model = detail.getModel();
        model.setTitle("第一次改名");
        first.setModel(model.toMap());
        templateService.update(first);

        // 再拿改动前的版本号提交：并发下后到的一方不能把前一个人的改动盖掉
        ScmPrintTemplateForm stale = first;
        stale.setVersion(detail.getVersion());
        var other = templateService.detail(detail.getId()).getModel();
        other.setTitle("第二次改名");
        stale.setModel(other.toMap());

        assertThatThrownBy(() -> templateService.update(stale))
                .as("乐观锁必须拒掉过期版本")
                .isInstanceOf(com.xsy.scm.common.exception.ScmBusinessException.class);
    }
}
