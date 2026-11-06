package com.xsy.scm.supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.xsy.scm.base.common.domain.PageParam;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.SystemEnvironment;
import com.xsy.scm.base.handler.GlobalExceptionHandler;
import com.xsy.scm.businesscategory.domain.entity.BusinessCategoryEntity;
import com.xsy.scm.businesscategory.domain.form.BusinessCategoryAddForm;
import com.xsy.scm.businesscategory.domain.form.BusinessCategoryUpdateForm;
import com.xsy.scm.businesscategory.service.BusinessCategoryService;
import com.xsy.scm.common.ScmW2PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.handler.ScmExceptionHandler;
import com.xsy.scm.supplier.controller.SupplierController;
import com.xsy.scm.supplier.controller.SupplierSkuController;
import com.xsy.scm.supplier.domain.entity.SupplierSkuEntity;
import com.xsy.scm.supplier.domain.form.SupplierAddForm;
import com.xsy.scm.supplier.domain.form.SupplierDeleteForm;
import com.xsy.scm.supplier.domain.form.SupplierQueryForm;
import com.xsy.scm.supplier.domain.form.SupplierSkuItemForm;
import com.xsy.scm.supplier.domain.form.SupplierSkuQueryForm;
import com.xsy.scm.supplier.domain.form.SupplierSkuReplaceForm;
import com.xsy.scm.supplier.domain.form.SupplierStatusForm;
import com.xsy.scm.supplier.domain.form.SupplierUpdateForm;
import com.xsy.scm.supplier.domain.vo.SupplierDetailVO;
import com.xsy.scm.supplier.domain.vo.SupplierOptionVO;
import com.xsy.scm.supplier.domain.vo.SupplierSkuVO;
import com.xsy.scm.supplier.domain.vo.SupplierVO;
import com.xsy.scm.supplier.manager.SupplierSkuChangeSet;
import com.xsy.scm.supplier.manager.SupplierSkuReferencePriceComparison;
import com.xsy.scm.supplier.manager.SupplierValidator;
import com.xsy.scm.supplier.service.SupplierQueryService;
import com.xsy.scm.supplier.service.SupplierService;
import com.xsy.scm.supplier.service.SupplierSkuService;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.supplier.constant.SupplierErrorCode.SKU_DISABLED;
import static com.xsy.scm.supplier.constant.SupplierErrorCode.SUPPLIER_NOT_FOUND;
import static com.xsy.scm.supplier.constant.SupplierErrorCode.SUPPLIER_SKU_DUPLICATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SupplierTest {

    @Nested
    @WebMvcTest(SupplierController.class)
    @AutoConfigureMockMvc(addFilters = false)
    @ContextConfiguration(classes = {SupplierController.class, ScmExceptionHandler.class, GlobalExceptionHandler.class})
    class SupplierControllerTest {
        @Autowired
        private MockMvc mvc;

        @MockitoBean
        private SupplierService service;

        @MockitoBean
        private SupplierQueryService queryService;

        @MockitoBean(name = "systemEnvironment")
        private SystemEnvironment environment;

        @Test
        @DisplayName("列表返回分页信封")
        void returnsEnvelope() throws Exception {
            PageResult<SupplierVO> page = new PageResult<>();
            page.setPageNum(1L);
            page.setPageSize(20L);
            page.setTotal(0L);
            page.setPages(0L);
            page.setList(List.of());
            page.setEmptyFlag(true);
            when(queryService.query(any())).thenReturn(page);

            mvc.perform(post("/scm/supplier/query").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pageNum\":1,\"pageSize\":20}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.list").isArray());
        }

        @Test
        @DisplayName("新增返回主键 Long")
        void addReturnsId() throws Exception {
            when(service.add(any())).thenReturn(77L);

            mvc.perform(post("/scm/supplier/add").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierCode\":\"S001\",\"name\":\"基地\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(77));
        }

        @Test
        @DisplayName("供应商不存在 → 40440")
        void mapsNotFound() throws Exception {
            when(queryService.detail(1L)).thenThrow(new ScmBusinessException(SUPPLIER_NOT_FOUND));

            mvc.perform(get("/scm/supplier/detail/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40440));
        }

        @Test
        @DisplayName("乐观锁冲突 → 40921")
        void mapsVersionConflict() throws Exception {
            doThrow(new ScmBusinessException(VERSION_CONFLICT)).when(service).update(any());

            mvc.perform(post("/scm/supplier/update").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierId\":1,\"version\":0,\"supplierCode\":\"S001\",\"name\":\"基地\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40921));
        }

        @Test
        @DisplayName("更新体即使带 status 也不会被 DTO 接收（legacy 不变量 S6）")
        void updateFormCannotCarryStatus() throws Exception {
            mvc.perform(post("/scm/supplier/update").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierId\":1,\"version\":0,\"supplierCode\":\"S001\",\"name\":\"基地\",\"status\":\"DISABLED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            // 约定层面证明：SupplierUpdateForm 及其父类都没有 status 属性
            List<String> fieldNames = new java.util.ArrayList<>();
            for (Class<?> clazz = SupplierUpdateForm.class; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                Arrays.stream(clazz.getDeclaredFields()).map(Field::getName).forEach(fieldNames::add);
            }
            assertThat(fieldNames).doesNotContain("status");
        }

        @Test
        @DisplayName("新增体同样不接受 status：初始状态由服务端固定为 ENABLED")
        void addFormCannotCarryStatus() {
            List<String> fieldNames = new java.util.ArrayList<>();
            Class<?> clazz = com.xsy.scm.supplier.domain.form.SupplierAddForm.class;
            for (; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                Arrays.stream(clazz.getDeclaredFields()).map(Field::getName).forEach(fieldNames::add);
            }
            assertThat(fieldNames).doesNotContain("status");
        }
    }

    @Nested
    class SupplierPgIT extends com.xsy.scm.common.ScmW2PgITBase {
        @Nested
        @DisplayName("供应商：读路径与 skuCount（PG IT）")
        class SupplierQueryServiceIT {
            @Autowired
            private SupplierService service;

            @Autowired
            private SupplierQueryService queryService;

            @Autowired
            private SupplierSkuService skuService;

            private SupplierAddForm form(String suffix) {
                SupplierAddForm form = new SupplierAddForm();
                form.setName("供应商" + suffix);
                // 编码由服务端生成、不再携带测试前缀；用联系人字段承载前缀，保证 keyword 查询能收敛到本用例的数据
                form.setContactName("联系人" + prefix);
                return form;
            }

            private SupplierQueryForm queryForm() {
                SupplierQueryForm form = new SupplierQueryForm();
                form.setPageNum(1L);
                form.setPageSize(50L);
                form.setKeyword(prefix);
                return form;
            }

            @Test
            @DisplayName("关键字覆盖编码 / 名称 / 联系人 / 联系电话")
            void keywordCoversCodeNameContactAndPhone() {
                SupplierAddForm form = form("K1");
                form.setContactName(prefix + "CONTACT-NAME");
                form.setContactPhone("13900002222");
                Long id = service.add(form);
                String generatedCode = jdbc.queryForObject(
                        "SELECT supplier_code FROM xsy_supplier WHERE id = ?", String.class, id);

                for (String keyword : List.of("供应商K1", generatedCode, prefix + "CONTACT-NAME", "13900002222")) {
                    SupplierQueryForm query = queryForm();
                    query.setKeyword(keyword);
                    assertThat(queryService.query(query).getList())
                            .as("关键字 %s 必须命中新建供应商", keyword)
                            .extracting(SupplierVO::getSupplierId).contains(id);
                }
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
            @DisplayName("供应商分类名称批量补全，并可按分类筛选")
            void businessCategoryNameIsEnrichedAndFilterable() {
                Long categoryId = jdbc.queryForObject(
                    "SELECT id FROM xsy_business_category WHERE scope = 'SUPPLIER' AND category_code = 'SUPPLIER_EGG'",
                    Long.class);
                SupplierAddForm form = form("BC");
                form.setBusinessCategoryId(categoryId);
                Long id = service.add(form);

                SupplierQueryForm byCategory = queryForm();
                byCategory.setBusinessCategoryId(categoryId);
                SupplierVO row = queryService.query(byCategory).getList().stream()
                    .filter(vo -> vo.getSupplierId().equals(id)).findFirst().orElseThrow();
                assertThat(row.getBusinessCategoryId()).isEqualTo(categoryId);
                assertThat(row.getBusinessCategoryName()).isEqualTo("蛋");

                SupplierDetailVO detail = queryService.detail(id);
                assertThat(detail.getBusinessCategoryId()).isEqualTo(categoryId);
                assertThat(detail.getBusinessCategoryName()).isEqualTo("蛋");

                Long otherCategoryId = jdbc.queryForObject(
                    "SELECT id FROM xsy_business_category WHERE scope = 'SUPPLIER' AND category_code = 'SUPPLIER_MEAT'",
                    Long.class);
                SupplierQueryForm otherCategory = queryForm();
                otherCategory.setBusinessCategoryId(otherCategoryId);
                assertThat(queryService.query(otherCategory).getList()).extracting(SupplierVO::getSupplierId)
                    .doesNotContain(id);
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
                assertThat(detail.getSupplierCode()).matches("^SUP\\d{6,}$");
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

        @Nested
        @DisplayName("供应商：写路径（PG IT）")
        class SupplierServiceIT {
            @Autowired
            private SupplierService service;

            @Autowired
            private SupplierSkuService skuService;

            @Autowired
            private BusinessCategoryService businessCategoryService;

            private SupplierAddForm form(String suffix) {
                SupplierAddForm form = new SupplierAddForm();
                form.setName("供应商" + suffix);
                return form;
            }

            private SupplierUpdateForm updateForm(Long id, int version, String suffix) {
                SupplierUpdateForm form = new SupplierUpdateForm();
                form.setSupplierId(id);
                form.setVersion(version);
                form.setName("改名" + suffix);
                return form;
            }

            private String supplierCode(Long id) {
                return jdbc.queryForObject("SELECT supplier_code FROM xsy_supplier WHERE id = ?", String.class, id);
            }

            @Test
            @DisplayName("新建强制 ENABLED（S7），编码由服务端生成（SUP######），审计写入操作人")
            void addForcesEnabledAndGeneratesCode() {
                Long id = service.add(form("a"));

                Map<String, Object> row = jdbc.queryForMap(
                        "SELECT supplier_code, status, version, deleted, created_by FROM xsy_supplier WHERE id = ?", id);
                assertThat((String) row.get("supplier_code")).matches("^SUP\\d{6,}$");
                assertThat(row.get("status")).isEqualTo("ENABLED");
                assertThat(((Number) row.get("version")).intValue()).isZero();
                assertThat(row.get("deleted")).isEqualTo(false);
                assertThat(row.get("created_by")).isEqualTo("1:1");
            }

            @Test
            @DisplayName("编码由服务端生成且不重号：同名两次创建都成功、编码不同；软删后可重建")
            void generatedCodesAreUniqueAndRecreateAfterDeleteSucceeds() {
                Long first = service.add(form("b"));
                Long second = service.add(form("b"));
                String firstCode = supplierCode(first);
                String secondCode = supplierCode(second);
                assertThat(firstCode).matches("^SUP\\d{6,}$");
                assertThat(secondCode).matches("^SUP\\d{6,}$").isNotEqualTo(firstCode);

                SupplierDeleteForm deletion = new SupplierDeleteForm();
                deletion.setSupplierId(first);
                deletion.setVersion(0);
                service.delete(deletion);

                Long recreated = service.add(form("b"));
                assertThat(recreated).as("软删后重建成功").isNotEqualTo(first);
                assertThat(supplierCode(recreated)).matches("^SUP\\d{6,}$").isNotEqualTo(firstCode);
            }

            @Test
            @DisplayName("update 不触碰 status：先停用再编辑，状态保持 DISABLED（S6）")
            void updateNeverTouchesStatus() {
                Long id = service.add(form("c"));

                SupplierStatusForm status = new SupplierStatusForm();
                status.setSupplierId(id);
                status.setVersion(0);
                status.setStatus("DISABLED");
                service.updateStatus(status);

                service.update(updateForm(id, 1, "c"));

                assertThat(jdbc.queryForObject("SELECT status FROM xsy_supplier WHERE id = ?", String.class, id))
                        .isEqualTo("DISABLED");
                assertThat(jdbc.queryForObject("SELECT name FROM xsy_supplier WHERE id = ?", String.class, id))
                        .isEqualTo("改名c");
            }

            @Test
            @DisplayName("旧版本提交 → 40921；停用供应商不可用于采购（40940）")
            void staleVersionConflictsAndDisabledSupplierIsNotUsable() {
                Long id = service.add(form("d"));
                SupplierUpdateForm update = updateForm(id, 0, "d");
                service.update(update);
                expectCode(() -> service.update(update), 40921);

                SupplierStatusForm status = new SupplierStatusForm();
                status.setSupplierId(id);
                status.setVersion(0);
                status.setStatus("DISABLED");
                expectCode(() -> service.updateStatus(status), 40921);

                status.setVersion(1);
                service.updateStatus(status);
                expectCode(() -> service.requireEnabled(id), 40940);
            }

            @Test
            @DisplayName("供应商分类必须属于 SUPPLIER 作用域且启用；编辑可清空")
            void businessCategoryMustBeEnabledSupplierScope() {
                Long supplierCategory = jdbc.queryForObject(
                    "SELECT id FROM xsy_business_category WHERE scope = 'SUPPLIER' AND category_code = 'SUPPLIER_VEG'",
                    Long.class);
                Long customerCategory = jdbc.queryForObject(
                    "SELECT id FROM xsy_business_category WHERE scope = 'CUSTOMER' AND category_code = 'CUSTOMER_VEG'",
                    Long.class);

                SupplierAddForm form = form("bc1");
                form.setBusinessCategoryId(supplierCategory);
                Long id = service.add(form);
                assertThat(jdbc.queryForObject(
                    "SELECT business_category_id FROM xsy_supplier WHERE id = ?", Long.class, id))
                    .isEqualTo(supplierCategory);

                SupplierAddForm crossScope = form("bc2");
                crossScope.setBusinessCategoryId(customerCategory);
                expectCode(() -> service.add(crossScope), 40444);

                BusinessCategoryAddForm disabledForm = new BusinessCategoryAddForm();
                disabledForm.setScope("SUPPLIER");
                disabledForm.setCategoryCode(prefix + "DISABLED");
                disabledForm.setName(prefix + "停用分类");
                disabledForm.setSortOrder(0);
                disabledForm.setStatus("ENABLED");
                Long disabledId = businessCategoryService.add(disabledForm);
                BusinessCategoryEntity disabled = businessCategoryService.require(disabledId);
                BusinessCategoryUpdateForm disable = new BusinessCategoryUpdateForm();
                disable.setCategoryId(disabledId);
                disable.setVersion(disabled.getVersion());
                disable.setScope("SUPPLIER");
                disable.setCategoryCode(disabled.getCategoryCode());
                disable.setName(disabled.getName());
                disable.setSortOrder(disabled.getSortOrder());
                disable.setStatus("DISABLED");
                businessCategoryService.update(disable);

                SupplierAddForm disabledCategory = form("bc3");
                disabledCategory.setBusinessCategoryId(disabledId);
                expectCode(() -> service.add(disabledCategory), 40954);

                SupplierUpdateForm clear = updateForm(id, 0, "bc1");
                clear.setBusinessCategoryId(null);
                service.update(clear);
                assertThat(jdbc.queryForObject(
                    "SELECT business_category_id FROM xsy_supplier WHERE id = ?", Long.class, id)).isNull();
            }

            @Test
            @DisplayName("被活动商品关联引用时拒绝删除（40947）；清空关联后即可删除")
            void deleteIsBlockedWhileSkuRelationsExist() {
                Long id = service.add(form("e"));
                Long skuId = newOnShelfSku("E1");

                SupplierSkuReplaceForm replace = new SupplierSkuReplaceForm();
                replace.setSupplierId(id);
                SupplierSkuItemForm item = new SupplierSkuItemForm();
                item.setSkuId(skuId);
                item.setPurchaseUnit("kg");
                replace.setItems(List.of(item));
                skuService.replace(replace);

                SupplierDeleteForm deletion = new SupplierDeleteForm();
                deletion.setSupplierId(id);
                deletion.setVersion(0);
                expectCode(() -> service.delete(deletion), 40947);

                replace.setItems(List.of());
                skuService.replace(replace);

                service.delete(deletion);
                assertThat(jdbc.queryForObject("SELECT deleted FROM xsy_supplier WHERE id = ?", Boolean.class, id)).isTrue();
            }

            @Test
            @DisplayName("不存在 / 已删除供应商统一 40440")
            void missingSupplierIs40440() {
                expectCode(() -> service.require(-1L), 40440);
                expectCode(() -> service.require(null), 40440);
            }
        }

        @Nested
        @DisplayName("商品-供应商关系：整表替换与保存的数据（PG IT）")
        class SupplierSkuServiceIT {
            @Autowired
            private SupplierService supplierService;

            @Autowired
            private SupplierSkuService skuService;

            private Long supplier(String suffix) {
                SupplierAddForm form = new SupplierAddForm();
                form.setName("供应商" + suffix);
                return supplierService.add(form);
            }

            private SupplierSkuItemForm item(Long skuId) {
                SupplierSkuItemForm item = new SupplierSkuItemForm();
                item.setSkuId(skuId);
                item.setPurchaseUnit("kg");
                item.setDefaultFlag(false);
                return item;
            }

            private SupplierSkuItemForm item(Long id, Integer version, Long skuId) {
                SupplierSkuItemForm item = item(skuId);
                item.setId(id);
                item.setVersion(version);
                return item;
            }

            private void replace(Long supplierId, List<SupplierSkuItemForm> items) {
                SupplierSkuReplaceForm form = new SupplierSkuReplaceForm();
                form.setSupplierId(supplierId);
                form.setItems(items);
                skuService.replace(form);
            }

            private Map<String, Object> row(Long id) {
                return jdbc.queryForMap(
                        "SELECT supplier_id, sku_id, supplier_code_snapshot, supplier_name_snapshot, "
                                + "sku_code_snapshot, sku_name_snapshot, purchase_unit, reference_price, "
                                + "purchaser_id, is_default, status, version, deleted, created_by "
                                + "FROM xsy_supplier_sku WHERE id = ?", id);
            }

            @Test
            @DisplayName("首次写入历史资料：供应商与商品在关联建立后被改名也不影响已存保存的数据")
            void replaceFreezesSnapshots() {
                Long supplierId = supplier("a");
                Long skuId = newOnShelfSku("A1");

                SupplierSkuItemForm item = item(skuId);
                item.setReferencePrice("12.3456");
                item.setDefaultFlag(true);
                item.setPurchaserId(anyEmployeeId());
                replace(supplierId, List.of(item));

                Long id = jdbc.queryForObject(
                        "SELECT id FROM xsy_supplier_sku WHERE supplier_id = ? AND sku_id = ? AND deleted = FALSE",
                        Long.class, supplierId, skuId);
                Map<String, Object> row = row(id);

                assertThat(row.get("supplier_code_snapshot")).isEqualTo(
                        jdbc.queryForObject("SELECT supplier_code FROM xsy_supplier WHERE id = ?", String.class, supplierId));
                assertThat(row.get("supplier_name_snapshot")).isEqualTo("供应商a");
                assertThat(row.get("sku_code_snapshot")).isEqualTo(skuCodeOf(skuId));
                assertThat((String) row.get("sku_name_snapshot"))
                        .as("legacy R4：名称取 SPU 名称，不是 SKU 规格名")
                        .isEqualTo(prefix + "A1商品");
                assertThat(row.get("purchase_unit")).isEqualTo("kg");
                assertThat((BigDecimal) row.get("reference_price")).isEqualByComparingTo("12.3456");
                assertThat(row.get("is_default")).isEqualTo(true);
                assertThat(row.get("status")).isEqualTo("ENABLED");
                assertThat(((Number) row.get("version")).intValue()).isZero();
                assertThat(row.get("deleted")).isEqualTo(false);
                assertThat(row.get("created_by")).isEqualTo("1:1");
            }

            @Test
            @DisplayName("差量替换：保留行保持主键、版本推进；被移除行软删且不影响保留行")
            void retainsIdentityAndSoftDeletesRemoved() {
                Long supplierId = supplier("b");
                Long firstSku = newOnShelfSku("B1");
                Long secondSku = newOnShelfSku("B2");
                replace(supplierId, List.of(item(firstSku), item(secondSku)));

                Long keptId = jdbc.queryForObject(
                        "SELECT id FROM xsy_supplier_sku WHERE supplier_id = ? AND sku_id = ? AND deleted = FALSE",
                        Long.class, supplierId, firstSku);
                Long removedId = jdbc.queryForObject(
                        "SELECT id FROM xsy_supplier_sku WHERE supplier_id = ? AND sku_id = ? AND deleted = FALSE",
                        Long.class, supplierId, secondSku);

                SupplierSkuItemForm kept = item(keptId, 0, firstSku);
                kept.setPurchaseUnit("件");
                kept.setReferencePrice("9.5");
                replace(supplierId, List.of(kept));

                Map<String, Object> keptRow = row(keptId);
                assertThat(keptRow.get("purchase_unit")).isEqualTo("件");
                assertThat((BigDecimal) keptRow.get("reference_price")).isEqualByComparingTo("9.5000");
                assertThat(((Number) keptRow.get("version")).intValue()).as("保留行版本推进到 1").isEqualTo(1);
                assertThat(keptRow.get("deleted")).isEqualTo(false);

                assertThat(jdbc.queryForObject("SELECT deleted FROM xsy_supplier_sku WHERE id = ?", Boolean.class, removedId))
                        .isTrue();
                assertThat(skuService.listBySupplierId(supplierId)).extracting(SupplierSkuVO::getId)
                        .containsExactly(keptId);
            }

            @Test
            @DisplayName("空数组 = 清空全部关联（R11），不是无操作")
            void emptyItemsClearsAll() {
                Long supplierId = supplier("c");
                replace(supplierId, List.of(item(newOnShelfSku("C1")), item(newOnShelfSku("C2"))));

                replace(supplierId, List.of());

                assertThat(skuService.listBySupplierId(supplierId)).isEmpty();
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_supplier_sku WHERE supplier_id = ? AND deleted = FALSE",
                        Integer.class, supplierId)).isZero();
            }

            @Test
            @DisplayName("R12：同一供应商允许多条默认来源，绝不发明「只允许一条默认」的基数策略")
            void multipleDefaultsAreAllowed() {
                Long supplierId = supplier("d");
                Long firstSku = newOnShelfSku("D1");
                Long secondSku = newOnShelfSku("D2");

                SupplierSkuItemForm first = item(firstSku);
                first.setDefaultFlag(true);
                SupplierSkuItemForm second = item(secondSku);
                second.setDefaultFlag(true);
                replace(supplierId, List.of(first, second));

                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_supplier_sku WHERE supplier_id = ? AND is_default = TRUE AND deleted = FALSE",
                        Integer.class, supplierId)).isEqualTo(2);

                // 走服务读路径再断言一次：{@code is_default} 列名与实体属性 {@code defaultFlag} 不同源，
                // 自定义 resultMap 若漏掉显式映射，自动驼峰映射会把它折成 {@code isDefault} 而静默丢值
                // —— 只查裸 SQL 是抓不到这个回归的。
                List<SupplierSkuVO> readBack = skuService.listBySupplierId(supplierId);
                assertThat(readBack).hasSize(2);
                assertThat(readBack).allSatisfy(row -> assertThat(row.getDefaultFlag()).isTrue());
                assertThat(readBack).extracting(SupplierSkuVO::getSkuId)
                        .containsExactlyInAnyOrder(firstSku, secondSku);
            }

            @Test
            @DisplayName("清空后重新添加同一 SKU 不撞 uk_supplier_sku_active（partial index）")
            void reAddAfterClearDoesNotViolateUniqueIndex() {
                Long supplierId = supplier("e");
                Long skuId = newOnShelfSku("E1");

                replace(supplierId, List.of(item(skuId)));
                replace(supplierId, List.of());
                replace(supplierId, List.of(item(skuId)));

                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_supplier_sku WHERE supplier_id = ? AND sku_id = ? AND deleted = FALSE",
                        Integer.class, supplierId, skuId)).isEqualTo(1);
            }

            @Test
            @DisplayName("请求内 skuId 重复 → 40943")
            void duplicateSkuIdInRequestRejected() {
                Long supplierId = supplier("f");
                Long skuId = newOnShelfSku("F1");

                expectCode(() -> replace(supplierId, List.of(item(skuId), item(skuId))), 40943);
            }

            @Test
            @DisplayName("跨供应商串用 id → 40943")
            void crossSupplierIdRejected() {
                Long first = supplier("g1");
                Long second = supplier("g2");
                Long skuId = newOnShelfSku("G1");

                replace(first, List.of(item(skuId)));
                Long otherRowId = jdbc.queryForObject(
                        "SELECT id FROM xsy_supplier_sku WHERE supplier_id = ? AND deleted = FALSE", Long.class, first);

                expectCode(() -> replace(second, List.of(item(otherRowId, 0, skuId))), 40943);
            }

            @Test
            @DisplayName("既有行的 skuId 不允许变更 → 40943（换货等价于删旧增新）")
            void skuIdChangeOnExistingRowRejected() {
                Long supplierId = supplier("h");
                Long firstSku = newOnShelfSku("H1");
                Long secondSku = newOnShelfSku("H2");
                replace(supplierId, List.of(item(firstSku)));
                Long rowId = jdbc.queryForObject(
                        "SELECT id FROM xsy_supplier_sku WHERE supplier_id = ? AND deleted = FALSE", Long.class, supplierId);

                expectCode(() -> replace(supplierId, List.of(item(rowId, 0, secondSku))), 40943);
            }

            @Test
            @DisplayName("不存在的 SKU → 40942")
            void unknownSkuRejected() {
                Long supplierId = supplier("i");
                expectCode(() -> replace(supplierId, List.of(item(9_000_000_001L))), 40942);
            }

            @Test
            @DisplayName("默认采购员必须存在（40443）")
            void unknownPurchaserRejected() {
                Long supplierId = supplier("j");
                Long skuId = newOnShelfSku("J1");
                SupplierSkuItemForm item = item(skuId);
                item.setPurchaserId(9_000_000_001L);

                expectCode(() -> replace(supplierId, List.of(item)), 40443);

                item.setPurchaserId(anyEmployeeId());
                replace(supplierId, List.of(item));
                assertThat(jdbc.queryForObject(
                        "SELECT purchaser_id FROM xsy_supplier_sku WHERE supplier_id = ? AND deleted = FALSE",
                        Long.class, supplierId)).isNotNull();
            }

            @Test
            @DisplayName("停用供应商不可维护关联（40940）")
            void disabledSupplierRejected() {
                Long supplierId = supplier("k");
                Long skuId = newOnShelfSku("K1");
                replace(supplierId, List.of(item(skuId)));

                SupplierStatusForm status = new SupplierStatusForm();
                status.setSupplierId(supplierId);
                status.setVersion(0);
                status.setStatus("DISABLED");
                supplierService.updateStatus(status);

                expectCode(() -> replace(supplierId, List.of(item(skuId))), 40940);
            }

            @Test
            @DisplayName("带 id 的行版本不一致 → 40921")
            void staleRowVersionRejected() {
                Long supplierId = supplier("l");
                Long skuId = newOnShelfSku("L1");
                replace(supplierId, List.of(item(skuId)));
                Long rowId = jdbc.queryForObject(
                        "SELECT id FROM xsy_supplier_sku WHERE supplier_id = ? AND deleted = FALSE", Long.class, supplierId);

                expectCode(() -> replace(supplierId, List.of(item(rowId, 7, skuId))), 40921);
            }

            @Test
            @DisplayName("金额与 JSONB 约定：4 位小数字符串、null 保持 null、商品规格资料可读")
            void decimalAndJsonbContract() throws Exception {
                Long supplierId = supplier("m");
                Long skuId = newOnShelfSku("M1");
                SupplierSkuItemForm item = item(skuId);
                item.setReferencePrice("7.5");
                replace(supplierId, List.of(item));

                SupplierSkuVO vo = skuService.listBySupplierId(supplierId).getFirst();
                JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(vo));
                assertThat(node.get("referencePrice").asText()).isEqualTo("7.5000");
                assertThat(node.get("specValuesSnapshot").get("规格").asText()).isEqualTo("M1");

                // 参考价可空：清空后必须回到 null，而不是 0.0000
                SupplierSkuItemForm cleared = item(vo.getId(), vo.getVersion(), skuId);
                cleared.setReferencePrice(null);
                replace(supplierId, List.of(cleared));
                assertThat(jdbc.queryForObject(
                        "SELECT reference_price FROM xsy_supplier_sku WHERE id = ?", BigDecimal.class, vo.getId())).isNull();
                JsonNode after = objectMapper.readTree(objectMapper.writeValueAsString(
                        skuService.listBySupplierId(supplierId).getFirst()));
                assertThat(after.get("referencePrice").isNull()).as("null 不得被写成 0.0000").isTrue();
            }

            @Test
            @DisplayName("只读反查分页按 supplierId / skuId / status 过滤，并补全采购员姓名")
            void queryFiltersAndEnrichesPurchaserName() {
                Long supplierId = supplier("n");
                Long skuId = newOnShelfSku("N1");
                SupplierSkuItemForm item = item(skuId);
                item.setPurchaserId(anyEmployeeId());
                replace(supplierId, List.of(item));

                SupplierSkuQueryForm form = new SupplierSkuQueryForm();
                form.setPageNum(1L);
                form.setPageSize(20L);
                form.setSupplierId(supplierId);
                form.setSkuId(skuId);
                List<SupplierSkuVO> list = skuService.query(form).getList();
                assertThat(list).hasSize(1);
                assertThat(list.getFirst().getPurchaserName()).isNotBlank();

                SupplierSkuQueryForm byStatus = new SupplierSkuQueryForm();
                byStatus.setPageNum(1L);
                byStatus.setPageSize(20L);
                byStatus.setSupplierId(supplierId);
                byStatus.setStatus("DISABLED");
                assertThat(skuService.query(byStatus).getList()).isEmpty();
            }

            @Test
            @DisplayName("W3 采购入口：无关联时 40442")
            void requireEnabledForPurchasingRejectsUnknownRelation() {
                Long supplierId = supplier("o1");
                Long skuId = newOnShelfSku("O1");

                expectCode(() -> skuService.requireEnabledForPurchasing(supplierId, skuId), 40442);
            }

            @Test
            @DisplayName("W3 采购入口：关联存在且 SPU/SKU 同时上架时可采购")
            void requireEnabledForPurchasingAcceptsOrderableRelation() {
                Long supplierId = supplier("o2");
                Long skuId = newOnShelfSku("O2");
                replace(supplierId, List.of(item(skuId)));

                assertThat(skuService.requireEnabledForPurchasing(supplierId, skuId).getSkuId()).isEqualTo(skuId);
            }

            /**
             * 制造「关联仍在，但 SPU 已下架」的脏状态（正常写入校验不会让它出现），
             * 用来证明供应商域自己的守卫独立成立。
             *
             * <p>这个用例必须独立成一个方法：SPU 的下架是通过 {@code JdbcTemplate} 直接改库完成的，
             * 而 MyBatis 的一级缓存只会在「MyBatis 自己的写语句」后被清空，
             * 因此如果在同一个 SqlSession 里先查过一次 {@code selectEnabledBySkuId}，第二次会命中旧缓存。
             * 每个测试方法各自一个事务 = 各自一个 SqlSession，天然避开这个陷阱。
             */
            @Test
            @DisplayName("W3 采购入口：SPU 已下架时 40942")
            void requireEnabledForPurchasingRejectsOffShelfSpu() {
                Long supplierId = supplier("o3");
                Long skuId = newOnShelfSku("O3");
                replace(supplierId, List.of(item(skuId)));

                jdbc.update("UPDATE xsy_product_spu SET status = 'OFF_SHELF' WHERE id = "
                        + "(SELECT spu_id FROM xsy_product_sku WHERE id = ?)", skuId);

                expectCode(() -> skuService.requireEnabledForPurchasing(supplierId, skuId), 40942);
            }
        }
    }

    @Nested
    @WebMvcTest(SupplierSkuController.class)
    @AutoConfigureMockMvc(addFilters = false)
    @ContextConfiguration(classes = {SupplierSkuController.class, ScmExceptionHandler.class, GlobalExceptionHandler.class})
    class SupplierSkuControllerTest {
        @Autowired
        private MockMvc mvc;

        @MockitoBean
        private SupplierSkuService service;

        @MockitoBean(name = "systemEnvironment")
        private SystemEnvironment environment;

        @Test
        @DisplayName("按供应商回填：返回数组信封")
        void listsBySupplier() throws Exception {
            when(service.listBySupplierId(3L)).thenReturn(List.of());

            mvc.perform(get("/scm/supplier/sku/list/3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("反查分页：返回分页信封")
        void queryReturnsEnvelope() throws Exception {
            PageResult<SupplierSkuVO> page = new PageResult<>();
            page.setPageNum(1L);
            page.setPageSize(20L);
            page.setTotal(0L);
            page.setPages(0L);
            page.setList(List.of());
            page.setEmptyFlag(true);
            when(service.query(any())).thenReturn(page);

            mvc.perform(post("/scm/supplier/sku/query").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pageNum\":1,\"pageSize\":20,\"skuId\":5}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.list").isArray());
        }

        @Test
        @DisplayName("空数组 = 清空全部关联（请求确实送达服务层，不是被当成无操作）")
        void emptyItemsMeansClearAll() throws Exception {
            mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierId\":3,\"items\":[]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            ArgumentCaptor<SupplierSkuReplaceForm> captor = ArgumentCaptor.forClass(SupplierSkuReplaceForm.class);
            verify(service).replace(captor.capture());
            assertThat(captor.getValue().getSupplierId()).isEqualTo(3L);
            assertThat(captor.getValue().getItems()).isEmpty();
        }

        @Test
        @DisplayName("参考价必须是 JSON 字符串：数字字面量被拒绝")
        void rejectsNumericLiteralReferencePrice() throws Exception {
            mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierId\":3,\"items\":[{\"skuId\":5,\"purchaseUnit\":\"箱\",\"referencePrice\":12.34,\"defaultFlag\":false}]}"))
                    .andExpect(jsonPath("$.code").value(30001));
        }

        @Test
        @DisplayName("参考价超过 4 位小数被拒绝")
        void rejectsTooManyDecimals() throws Exception {
            mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierId\":3,\"items\":[{\"skuId\":5,\"purchaseUnit\":\"箱\",\"referencePrice\":\"12.34000\",\"defaultFlag\":false}]}"))
                    .andExpect(jsonPath("$.code").value(30001));
        }

        @Test
        @DisplayName("参考价为 null 表示「未定价」，允许通过")
        void allowsNullReferencePrice() throws Exception {
            mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierId\":3,\"items\":[{\"skuId\":5,\"purchaseUnit\":\"箱\",\"referencePrice\":null,\"defaultFlag\":true}]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("SKU 未上架 → 40942")
        void mapsSkuDisabled() throws Exception {
            doThrow(new ScmBusinessException(SKU_DISABLED)).when(service).replace(any());

            mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierId\":3,\"items\":[{\"skuId\":5,\"purchaseUnit\":\"箱\",\"defaultFlag\":false}]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40942));
        }

        @Test
        @DisplayName("关联重复 → 40943")
        void mapsDuplicate() throws Exception {
            doThrow(new ScmBusinessException(SUPPLIER_SKU_DUPLICATE)).when(service).replace(any());

            mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierId\":3,\"items\":[{\"skuId\":5,\"purchaseUnit\":\"箱\",\"defaultFlag\":false}]}"))
                    .andExpect(jsonPath("$.code").value(40943));
        }

        @Test
        @DisplayName("版本冲突 → 40921")
        void mapsVersionConflict() throws Exception {
            doThrow(new ScmBusinessException(VERSION_CONFLICT)).when(service).replace(any());

            mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierId\":3,\"items\":[{\"id\":9,\"version\":0,\"skuId\":5,\"purchaseUnit\":\"箱\",\"defaultFlag\":false}]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40921));
        }

        @Test
        @DisplayName("采购单位必填")
        void requiresPurchaseUnit() throws Exception {
            mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierId\":3,\"items\":[{\"skuId\":5,\"defaultFlag\":false}]}"))
                    .andExpect(jsonPath("$.code").value(30001));
        }

        @Test
        @DisplayName("超过 500 行被拒绝（风险 R2 的请求体上限）")
        void rejectsOversizedPayload() throws Exception {
            StringBuilder items = new StringBuilder();
            for (int i = 0; i < 501; i++) {
                if (i > 0) {
                    items.append(',');
                }
                items.append("{\"skuId\":").append(i + 1).append(",\"purchaseUnit\":\"箱\",\"defaultFlag\":false}");
            }
            mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierId\":3,\"items\":[" + items + "]}"))
                    .andExpect(jsonPath("$.code").value(30001));
        }
    }

    @Nested
    class SupplierUnitTest {
        @Nested

        class SupplierSkuChangeSetTest {
            private static int codeOf(Throwable t) {
                return ((ScmBusinessException) t).getErrorCode().getCode();
            }

            @Test
            void supplierItemCodeIsExternalAndPreservesCaseAndZeros() {
                assertThat(SupplierSkuChangeSet.normalizeSupplierItemCode("  Ab-00007  ")).isEqualTo("Ab-00007");
                assertThat(SupplierSkuChangeSet.normalizeSupplierItemCode("    ")).isNull();
                assertThat(SupplierSkuChangeSet.normalizeSupplierItemCode(null)).isNull();
            }

            @Test
            void duplicateSupplierItemCodeCannotPointAtTwoSkus() {
                var a = item(null, null, 10L);
                a.setSupplierItemCode("  0007  ");
                var b = item(null, null, 11L);
                b.setSupplierItemCode("0007");
                assertThatThrownBy(() -> SupplierSkuChangeSet.between(List.of(), List.of(a, b)))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40943));
            }

            @Test
            void sameExternalCodeMayDifferByCaseAndNullCodeMayRepeat() {
                var a = item(null, null, 10L);
                a.setSupplierItemCode("ab-01");
                var b = item(null, null, 11L);
                b.setSupplierItemCode("AB-01");
                var c = item(null, null, 12L);
                assertThat(SupplierSkuChangeSet.between(List.of(), List.of(a, b, c)).inserted()).hasSize(3);
            }

            private static SupplierSkuEntity row(Long id, Long skuId, Integer version) {
                SupplierSkuEntity entity = new SupplierSkuEntity();
                entity.setId(id);
                entity.setSkuId(skuId);
                entity.setVersion(version);
                return entity;
            }

            private static SupplierSkuItemForm item(Long id, Integer version, Long skuId) {
                SupplierSkuItemForm form = new SupplierSkuItemForm();
                form.setId(id);
                form.setVersion(version);
                form.setSkuId(skuId);
                form.setPurchaseUnit("箱");
                return form;
            }

            @Test
            @DisplayName("保留 / 新增 / 删除三类动作分离")
            void separatesRetainedInsertedRemoved() {
                var keep = row(7L, 70L, 2);
                var removed = row(8L, 80L, 1);

                var keepForm = item(7L, 2, 70L);
                var addForm = item(null, null, 90L);

                var changeSet = SupplierSkuChangeSet.between(List.of(keep, removed), List.of(keepForm, addForm));

                assertThat(changeSet.retained()).hasSize(1);
                assertThat(changeSet.retained().get(0).existing().getId()).isEqualTo(7L);
                assertThat(changeSet.retained().get(0).requested()).isSameAs(keepForm);
                assertThat(changeSet.inserted()).containsExactly(addForm);
                assertThat(changeSet.removedIds()).containsExactly(8L);
            }

            @Test
            @DisplayName("带 id 但不在该供应商名下 → 40943")
            void rejectsForeignIdentity() {
                var existing = List.of(row(7L, 70L, 0));
                var foreign = item(99L, 0, 99L);

                assertThatThrownBy(() -> SupplierSkuChangeSet.between(existing, List.of(foreign)))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40943));
            }

            @Test
            @DisplayName("既有行的 skuId 被变更 → 40943（应删旧增新，而不是改行）")
            void rejectsSkuIdMutation() {
                var existing = List.of(row(7L, 70L, 0));
                var mutated = item(7L, 0, 71L);

                assertThatThrownBy(() -> SupplierSkuChangeSet.between(existing, List.of(mutated)))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40943));
            }

            @Test
            @DisplayName("请求内 skuId 重复 → 40943")
            void rejectsRepeatedSkuId() {
                var first = item(null, null, 90L);
                var second = item(null, null, 90L);

                assertThatThrownBy(() -> SupplierSkuChangeSet.between(List.of(), List.of(first, second)))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40943));
            }

            @Test
            @DisplayName("带 id 的行版本不一致 → 40921")
            void rejectsStaleVersion() {
                var existing = List.of(row(7L, 70L, 5));
                var stale = item(7L, 4, 70L);

                assertThatThrownBy(() -> SupplierSkuChangeSet.between(existing, List.of(stale)))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40921));
            }

            @Test
            @DisplayName("无 id 但 (supplierId, skuId) 已存在 → 复用既有行，不新增")
            void reusesExistingRowWhenSkuMatches() {
                var existing = List.of(row(7L, 70L, 3));
                var withoutId = item(null, null, 70L);

                var changeSet = SupplierSkuChangeSet.between(existing, List.of(withoutId));

                assertThat(changeSet.inserted()).isEmpty();
                assertThat(changeSet.removedIds()).isEmpty();
                assertThat(changeSet.retained()).hasSize(1);
                assertThat(changeSet.retained().get(0).existing().getId()).isEqualTo(7L);
            }

            @Test
            @DisplayName("无 id 复用但显式带了过期 version → 40921")
            void rejectsStaleVersionOnReuse() {
                var existing = List.of(row(7L, 70L, 3));
                var withoutId = item(null, 1, 70L);

                assertThatThrownBy(() -> SupplierSkuChangeSet.between(existing, List.of(withoutId)))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40921));
            }

            @Test
            @DisplayName("空数组 = 清空全部关联")
            void emptyRequestRemovesEverything() {
                var existing = List.of(row(1L, 10L, 0), row(2L, 20L, 0), row(3L, 30L, 0));

                var changeSet = SupplierSkuChangeSet.between(existing, List.of());

                assertThat(changeSet.retained()).isEmpty();
                assertThat(changeSet.inserted()).isEmpty();
                assertThat(changeSet.removedIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
            }

            @Test
            @DisplayName("null 请求列表按空处理（清空全部）")
            void nullRequestBehavesAsEmpty() {
                var changeSet = SupplierSkuChangeSet.between(List.of(row(1L, 10L, 0)), null);

                assertThat(changeSet.removedIds()).containsExactly(1L);
            }

            @Test
            @DisplayName("R12 防回归：同一供应商多条 defaultFlag=true 全部保留，不发明基数策略")
            void keepsMultipleDefaultsWithoutInventingCardinalityPolicy() {
                var first = row(1L, 10L, 0);
                var second = row(2L, 20L, 0);

                var firstForm = item(1L, 0, 10L);
                firstForm.setDefaultFlag(true);
                var secondForm = item(2L, 0, 20L);
                secondForm.setDefaultFlag(true);

                var changeSet = SupplierSkuChangeSet.between(List.of(first, second), List.of(firstForm, secondForm));

                assertThat(changeSet.retained()).hasSize(2);
                assertThat(changeSet.retained()).allSatisfy(m -> assertThat(m.requested().getDefaultFlag()).isTrue());
                assertThat(changeSet.inserted()).isEmpty();
                assertThat(changeSet.removedIds()).isEmpty();
            }

            @Test
            @DisplayName("空库 + 全新增：全部进 inserted")
            void allInsertWhenNothingExists() {
                var changeSet = SupplierSkuChangeSet.between(List.of(), List.of(item(null, null, 1L), item(null, null, 2L)));

                assertThat(changeSet.inserted()).hasSize(2);
                assertThat(changeSet.retained()).isEmpty();
                assertThat(changeSet.removedIds()).isEmpty();
            }
        }

        @Nested

        class SupplierSkuReferencePriceComparisonTest {
            @Test
            void comparesOnlyWithinSamePurchaseUnit() {
                var cheap = source(1L, "公斤", "10.0000");
                var expensive = source(2L, "公斤", "12.5000");
                var box = source(3L, "箱", "100.0000");

                var result = SupplierSkuReferencePriceComparison.compare(List.of(expensive, box, cheap));

                assertThat(result).hasSize(3);
                var boxQuote = result.stream().filter(row -> Long.valueOf(3).equals(row.getSupplierId()))
                        .findFirst().orElseThrow();
                assertThat(boxQuote.getPricedSupplierCount()).isEqualTo(1);
                assertThat(boxQuote.getPriceGapAmount()).isNull();

                var kgCheap = result.stream().filter(row -> Long.valueOf(1).equals(row.getSupplierId()))
                        .findFirst().orElseThrow();
                var kgExpensive = result.stream().filter(row -> Long.valueOf(2).equals(row.getSupplierId()))
                        .findFirst().orElseThrow();
                assertThat(kgCheap.getPriceGapAmount()).isEqualByComparingTo("0.0000");
                assertThat(kgCheap.getPriceGapPercent()).isEqualByComparingTo("0.0000");
                assertThat(kgExpensive.getLowestReferencePrice()).isEqualByComparingTo("10.0000");
                assertThat(kgExpensive.getPriceGapAmount()).isEqualByComparingTo("2.5000");
                assertThat(kgExpensive.getPriceGapPercent()).isEqualByComparingTo("25.0000");
                assertThat(kgExpensive.getPricedSupplierCount()).isEqualTo(2);
            }

            @Test
            void nullReferencePriceIsNotFreeOffer() {
                var missing = source(1L, "公斤", null);
                var only = source(2L, "公斤", "9.0000");

                var result = SupplierSkuReferencePriceComparison.compare(List.of(missing, only));

                assertThat(result).hasSize(2);
                assertThat(result.get(0).getPricedSupplierCount()).isEqualTo(1);
                assertThat(result.get(0).getPriceGapAmount()).isNull();
                assertThat(result.get(1).getReferencePrice()).isNull();
                assertThat(result.get(1).getLowestReferencePrice()).isEqualByComparingTo("9.0000");
                assertThat(result.get(1).getPriceGapPercent()).isNull();
            }

            @Test
            void zeroMinimumQuoteHasUnknownPercentButKnownAmount() {
                var zero = source(1L, "公斤", "0.0000");
                var paid = source(2L, "公斤", "5.0000");

                var result = SupplierSkuReferencePriceComparison.compare(List.of(zero, paid));

                assertThat(result.get(1).getPriceGapAmount()).isEqualByComparingTo("5.0000");
                assertThat(result.get(1).getPriceGapPercent()).isNull();
                assertThat(result.get(1).getLowestReferencePrice()).isEqualByComparingTo("0");
            }

            @Test
            void samePriceAndEmptySourcesAreHandled() {
                assertThat(SupplierSkuReferencePriceComparison.compare(null)).isEmpty();
                assertThat(SupplierSkuReferencePriceComparison.compare(List.of())).isEmpty();
                var result = SupplierSkuReferencePriceComparison.compare(List.of(
                        source(1L, "箱", "8.0000"), source(2L, "箱", "8.0000")));

                assertThat(result).hasSize(2);
                assertThat(result).allSatisfy(row -> {
                    assertThat(row.getPriceGapAmount()).isEqualByComparingTo("0.0000");
                    assertThat(row.getPriceGapPercent()).isEqualByComparingTo("0.0000");
                });
            }

            private static SupplierSkuVO source(Long supplierId, String unit, String referencePrice) {
                var row = new SupplierSkuVO();
                row.setSupplierId(supplierId);
                row.setSkuId(100L);
                row.setSupplierNameSnapshot("供货商" + supplierId);
                row.setPurchaseUnit(unit);
                row.setReferencePrice(referencePrice == null ? null : new BigDecimal(referencePrice));
                row.setDefaultFlag(false);
                return row;
            }
        }

        @Nested

        class SupplierValidatorTest {
            @Test
            @DisplayName("名称归一化：仅 trim，保留大小写")
            void normalizesName() {
                assertThat(SupplierValidator.normalizeName("  鲜蔬源基地  ")).isEqualTo("鲜蔬源基地");
                assertThat(SupplierValidator.normalizeName("Acme Farms")).isEqualTo("Acme Farms");
                assertThat(SupplierValidator.normalizeName(null)).isNull();
            }

            @Test
            @DisplayName("联系人 / 地址归一化：空白视作未填写")
            void normalizesContactFields() {
                assertThat(SupplierValidator.normalizeOptional("  李四  ")).isEqualTo("李四");
                assertThat(SupplierValidator.normalizeOptional("   ")).isNull();
                assertThat(SupplierValidator.normalizeOptional("")).isNull();
                assertThat(SupplierValidator.normalizeOptional(null)).isNull();
            }

            @Test
            @DisplayName("备注可以被清空：空白 → null（配合 FieldStrategy.ALWAYS 真正写 NULL）")
            void remarkCanBeCleared() {
                assertThat(SupplierValidator.normalizeOptional("   ")).isNull();
                assertThat(SupplierValidator.normalizeOptional("长期合作")).isEqualTo("长期合作");
            }
        }
    }
}
