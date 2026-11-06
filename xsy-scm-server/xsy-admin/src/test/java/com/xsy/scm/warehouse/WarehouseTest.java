package com.xsy.scm.warehouse;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.base.common.code.UserErrorCode;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.SystemEnvironment;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.common.exception.BusinessException;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.base.handler.GlobalExceptionHandler;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.handler.ScmExceptionHandler;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.purchase.constant.ScmReceiptModeEnum;
import com.xsy.scm.purchase.domain.form.PurchaseDemandGenerateForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderAddForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO;
import com.xsy.scm.purchase.service.PurchaseOrderService;
import com.xsy.scm.purchase.service.PurchaseQueryService;
import com.xsy.scm.warehouse.constant.ScmWarehouseStatusEnum;
import com.xsy.scm.warehouse.constant.WarehouseErrorCode;
import com.xsy.scm.warehouse.controller.WarehouseController;
import com.xsy.scm.warehouse.domain.form.WarehouseAddForm;
import com.xsy.scm.warehouse.domain.form.WarehouseQueryForm;
import com.xsy.scm.warehouse.domain.form.WarehouseStatusForm;
import com.xsy.scm.warehouse.domain.form.WarehouseUpdateForm;
import com.xsy.scm.warehouse.domain.vo.WarehouseVO;
import com.xsy.scm.warehouse.manager.WarehouseValidator;
import com.xsy.scm.warehouse.service.WarehouseQueryService;
import com.xsy.scm.warehouse.service.WarehouseService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WarehouseTest {

    @Nested
    @DisplayName("仓库启停生命周期（PG IT）")
    class WarehouseLifecycleIT extends ScmW6PgITBase {
        @Autowired
        private WarehouseQueryService warehouseQueryService;

        /**
         * 读库中的权威 version（jdbc 直读，绕开 MyBatis 一级缓存）。
         */
        private WarehouseStatusForm statusForm(Long id) {
            WarehouseStatusForm form = new WarehouseStatusForm();
            form.setId(id);
            form.setVersion(jdbc.queryForObject(
                    "SELECT version FROM xsy_warehouse WHERE id = ?", Integer.class, id));
            return form;
        }

        private String warehouseStatus(Long id) {
            return jdbc.queryForObject("SELECT status FROM xsy_warehouse WHERE id = ?", String.class, id);
        }

        /**
         * 造一张绑定到指定仓库的已提交采购单（无需求来源）。
         */
        private PurchaseOrderVO submittedOrderOn(Long warehouseId, String suffix, String quantity) {
            Long skuId = newOnShelfSku(suffix);
            Long supplierId = newPurchasableSupplier(suffix, skuId);
            PurchaseOrderVO order = purchaseOrderService.create(
                    orderForm(supplierId, warehouseId, skuId, quantity, "6.2000"),
                    prefix + ":" + suffix + ":po");
            return submitOrder(order.getId());
        }

        /**
         * 为采购单造一张 WAREHOUSE_CONFIRM 收货单。
         */
        private PurchaseReceiptVO warehouseConfirmReceipt(Long orderId, String suffix) {
            PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
            form.setPurchaseOrderId(orderId);
            form.setReceiptMode(ScmReceiptModeEnum.WAREHOUSE_CONFIRM.name());
            form.setRemark("WL IT 收货单");
            return purchaseReceiptService.create(form, prefix + ":" + suffix + ":receipt");
        }

        // ------------------------------------------------------------------
        // 1. 启停往返 + 状态非法
        // ------------------------------------------------------------------

        @Test
        @DisplayName("启停往返：disable → enable；重复同向操作 41004")
        void enableDisableRoundTrip() {
            Long w = newWarehouse("WL1");
            assertThat(warehouseStatus(w)).isEqualTo("ENABLED");

            warehouseService.disable(statusForm(w));
            assertThat(warehouseStatus(w)).isEqualTo("DISABLED");
            expectCode(() -> warehouseService.disable(statusForm(w)), 41004);

            warehouseService.enable(statusForm(w));
            assertThat(warehouseStatus(w)).isEqualTo("ENABLED");
            expectCode(() -> warehouseService.enable(statusForm(w)), 41004);
        }

        // ------------------------------------------------------------------
        // 2. 停用阻塞：库存余额
        // ------------------------------------------------------------------

        @Test
        @DisplayName("停用阻塞：有库存余额 → 41005")
        void balanceBlocksDisable() {
            Long w = newWarehouse("WL2");
            PurchaseOrderVO order = submittedOrderOn(w, "WL2", "5.0000");
            confirmReceipt(createReceipt(order.getId()).getId(), "5.0000");
            assertThat(balanceRow(w, order.getItems().getFirst().getSkuId()).getQuantity())
                    .isEqualByComparingTo("5.0000");

            expectCode(() -> warehouseService.disable(statusForm(w)), 41005);
        }

        // ------------------------------------------------------------------
        // 3. 停用阻塞：在途采购单
        // ------------------------------------------------------------------

        @Test
        @DisplayName("停用阻塞：SUBMITTED 采购单 → 41006")
        void submittedOrderBlocksDisable() {
            Long w = newWarehouse("WL3");
            submittedOrderOn(w, "WL3", "5.0000");
            expectCode(() -> warehouseService.disable(statusForm(w)), 41006);
        }

        @Test
        @DisplayName("停用阻塞：PARTIALLY_RECEIVED 采购单 → 41006")
        void partiallyReceivedOrderBlocksDisable() {
            Long w = newWarehouse("WL4");
            PurchaseOrderVO order = submittedOrderOn(w, "WL4", "10.0000");
            PurchaseReceiptVO receipt = warehouseConfirmReceipt(order.getId(), "WL4");
            confirmReceipt(receipt.getId(), "4.0000");
            assertThat(reloadOrder(order.getId()).getStatus()).isEqualTo("PARTIALLY_RECEIVED");

            expectCode(() -> warehouseService.disable(statusForm(w)), 41006);
        }

        // ------------------------------------------------------------------
        // 4. 停用阻塞：待入库收货单
        // ------------------------------------------------------------------

        @Test
        @DisplayName("停用阻塞：CONFIRMED 且 putaway=PENDING 收货单 → 41007")
        void pendingPutawayBlocksDisable() {
            Long w = newWarehouse("WL5");
            PurchaseOrderVO order = submittedOrderOn(w, "WL5", "5.0000");
            PurchaseReceiptVO receipt = warehouseConfirmReceipt(order.getId(), "WL5");
            confirmReceipt(receipt.getId(), "5.0000");
            assertThat(reloadOrder(order.getId()).getStatus()).isEqualTo("RECEIVED");
            assertThat(reloadReceipt(receipt.getId()).getPutawayStatus()).isEqualTo("PENDING");

            expectCode(() -> warehouseService.disable(statusForm(w)), 41007);
        }

        // ------------------------------------------------------------------
        // 5. 停用后：新引用拒绝，历史查询/详情照常
        // ------------------------------------------------------------------

        @Test
        @DisplayName("停用后：新需求/新采购单拒绝（40987），历史详情与选择器规则正确")
        void disabledWarehouseRejectsNewReferencesButAllowsHistory() {
            Long w = newWarehouse("WL6");
            warehouseService.disable(statusForm(w));

            // 新采购需求 → 40987
            PurchaseDemandGenerateForm gen = new PurchaseDemandGenerateForm();
            gen.setStartAt(OffsetDateTime.now().minusMinutes(1));
            gen.setEndAt(OffsetDateTime.now());
            gen.setWarehouseId(w);
            expectCode(() -> purchaseDemandService.generate(gen, prefix + ":WL6:gen"), 40987);

            // 新采购单 → 40987
            Long skuId = newOnShelfSku("WL6");
            Long supplierId = newPurchasableSupplier("WL6", skuId);
            expectCode(() -> purchaseOrderService.create(
                    orderForm(supplierId, w, skuId, "5.0000", "6.2000"), prefix + ":WL6:po"), 40987);

            // 历史详情照常（detail 不校验启用态）
            assertThat(warehouseQueryService.detail(w).getStatus()).isEqualTo("DISABLED");
            // 下拉选择器只列 ENABLED → 不含该停用仓库
            assertThat(warehouseQueryService.list()).noneMatch(x -> x.getId().equals(w));
        }

        @Test
        @DisplayName("停用后：新收货单拒绝（40987）")
        void disabledWarehouseRejectsNewReceipt() {
            Long w = newWarehouse("WL7");
            PurchaseOrderVO order = submittedOrderOn(w, "WL7", "5.0000");
            // 模拟「强制退役」：越过停用守卫直接置 DISABLED（正常流程因在途采购单被 41006 拦截）
            disableWarehouse(w);

            expectCode(() -> createReceipt(order.getId()), 40987);
        }
    }

    @Nested
    class WarehouseValidatorTest {
        @Test
        @DisplayName("名称归一化：只去首尾空白，不改大小写")
        void normalizesName() {
            assertThat(WarehouseValidator.normalizeName("  默认仓库 ")).isEqualTo("默认仓库");
            assertThat(WarehouseValidator.normalizeName("Main WH")).isEqualTo("Main WH");
            assertThat(WarehouseValidator.normalizeName(null)).isNull();
        }

        @Test
        @DisplayName("状态取值域：ENABLED / DISABLED 通过，其余与 null 拒绝")
        void validatesStatusDomain() {
            WarehouseValidator.validateStatus("ENABLED");
            WarehouseValidator.validateStatus("DISABLED");
            assertThatThrownBy(() -> WarehouseValidator.validateStatus("BROKEN"))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                            e -> assertThat(e.getErrorCode().getCode()).isEqualTo(VALIDATION_ERROR.getCode()));
            assertThatThrownBy(() -> WarehouseValidator.validateStatus(null))
                    .isInstanceOf(ScmBusinessException.class);
            assertThatThrownBy(() -> WarehouseValidator.validateStatus("enabled"))
                    .as("取值域大小写敏感：小写不是合法枚举名")
                    .isInstanceOf(ScmBusinessException.class);
        }

        @Test
        @DisplayName("名称必填：null 表单 / 名称缺失或全空白 → 40000；名称存在即通过")
        void validatesRequiredName() {
            assertThatThrownBy(() -> WarehouseValidator.validateRequired(null))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                            e -> assertThat(e.getErrorCode().getCode()).isEqualTo(VALIDATION_ERROR.getCode()));

            WarehouseAddForm form = new WarehouseAddForm();
            assertThatThrownBy(() -> WarehouseValidator.validateRequired(form))
                    .as("名称缺失")
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                            e -> assertThat(e.getErrorCode().getCode()).isEqualTo(VALIDATION_ERROR.getCode()));

            form.setName("   ");
            assertThatThrownBy(() -> WarehouseValidator.validateRequired(form))
                    .as("名称全空白")
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                            e -> assertThat(e.getErrorCode().getCode()).isEqualTo(VALIDATION_ERROR.getCode()));

            form.setName("仓");
            WarehouseValidator.validateRequired(form);
        }
    }

    @Nested
    class WarehouseW5PgIT extends ScmW5PgITBase {
        @Nested

        class PurchaseWarehouseIT {
            @Autowired
            private WarehouseQueryService queries;

            @Test
            @DisplayName("V15 播种 WH001 且为 ENABLED，仓库表只有这一行种子")
            void seedsSingleEnabledWarehouse() {
                Long id = seedWarehouseId();
                assertThat(id).isNotNull();

                String status = jdbc.queryForObject(
                        "SELECT status FROM xsy_warehouse WHERE id = ?", String.class, id);
                assertThat(status).isEqualTo(ScmWarehouseStatusEnum.ENABLED.name());
                assertThat(warehouseService.enabled(id)).isTrue();

                // G-03 单仓库规则：种子表达「系统仅维护一个启用仓库」
                assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM xsy_warehouse WHERE warehouse_code = ? AND deleted = FALSE",
                        Long.class, SEED_WAREHOUSE_CODE)).isEqualTo(1L);
            }

            @Test
            @DisplayName("编码由服务端生成（WH######）：同名两次创建都成功且不重号；编辑不触碰编码；过期版本 → 40921")
            void generatesUniqueCodesAndEnforcesOptimisticLock() {
                Long first = warehouseService.create(addForm("一号仓"));
                Long second = warehouseService.create(addForm("一号仓"));

                String firstCode = warehouseCode(first);
                String secondCode = warehouseCode(second);
                assertThat(firstCode).matches("^WH\\d{6,}$");
                assertThat(secondCode).matches("^WH\\d{6,}$").isNotEqualTo(firstCode);

                // 过期版本
                WarehouseUpdateForm stale = updateForm(first, "一号仓改名", 99);
                expectCode(() -> warehouseService.update(stale), VERSION_CONFLICT.getCode());

                // 正常编辑成功；编码不可改，服务端不读写表单里的编码
                warehouseService.update(updateForm(first, "一号仓改名", 0));
                assertThat(jdbc.queryForObject("SELECT name FROM xsy_warehouse WHERE id = ?", String.class, first))
                        .isEqualTo("一号仓改名");
                assertThat(jdbc.queryForObject("SELECT version FROM xsy_warehouse WHERE id = ?", Integer.class, first))
                        .isEqualTo(1);
                assertThat(warehouseCode(first)).as("编辑不触碰编码").isEqualTo(firstCode);
            }

            @Test
            @DisplayName("停用后仍可读；非法状态由后端校验拒绝")
            void disableKeepsRowReadableAndRejectsIllegalStatus() {
                Long id = newWarehouse("DIS");
                assertThat(warehouseService.enabled(id)).isTrue();

                disableWarehouse(id);
                assertThat(warehouseService.enabled(id)).isFalse();
                // 停用后仍然可读（管理页要能看到它），不是 404
                assertThat(warehouseService.require(id).getStatus())
                        .isEqualTo(ScmWarehouseStatusEnum.DISABLED.name());

                WarehouseQueryForm query = new WarehouseQueryForm();
                query.setStatus("BROKEN");
                assertThatThrownBy(() -> queries.query(query)).isInstanceOf(ScmBusinessException.class);
                assertThat(warehouseService.require(id).getStatus()).isEqualTo("DISABLED");
            }

            // ------------------------------------------------------------------

            private String warehouseCode(Long id) {
                return jdbc.queryForObject("SELECT warehouse_code FROM xsy_warehouse WHERE id = ?", String.class, id);
            }

            private static com.xsy.scm.warehouse.domain.form.WarehouseAddForm addForm(String name) {
                var form = new com.xsy.scm.warehouse.domain.form.WarehouseAddForm();
                form.setName(name);
                return form;
            }

            private static WarehouseUpdateForm updateForm(Long id, String name, Integer version) {
                var form = new WarehouseUpdateForm();
                form.setId(id);
                form.setName(name);
                form.setVersion(version);
                return form;
            }
        }

        @Nested
        @DisplayName("P0-H 仓库主档可见性（PG IT）")
        class WarehouseDataScopePgIT {
            @Autowired
            private WarehouseQueryService warehouseQueryService;

            @Autowired
            private PurchaseOrderService purchaseOrderService;

            @Autowired
            private PurchaseQueryService purchaseQueryService;

            @Test
            @DisplayName("选择器只返回授权仓：单仓授权看不到别的仓，全量范围授权才返回全部")
            void selectorReturnsOnlyAuthorizedWarehouses() {
                Long warehouseA = newWarehouse("SA");
                Long warehouseB = newWarehouse("SB");
                Long onlyA = scopedEmployee(warehouseA);
                Long none = newEmployee("NONE");

                assertThat(as(onlyA, Set.of(), this::selectorCodes))
                        .as("只有一个仓的授权，就不该出现第二个仓")
                        .containsExactly(warehouseA);
                assertThat(as(none, Set.of(), this::selectorCodes))
                        .as("没有任何授权行时是空列表，不是全量、也不是报错")
                        .isEmpty();
                // 放宽是显式授权的结果：同一个员工拿到 *:scope:all:query 后三个仓都出来
                assertThat(as(onlyA, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM), this::selectorCodes))
                        .contains(warehouseA, warehouseB, seedWarehouseId());
            }

            @Test
            @DisplayName("停用仓即使有授权也不进选择器：ENABLED 过滤与范围过滤是两条 AND")
            void disabledWarehouseStaysOutOfSelector() {
                Long warehouse = newWarehouse("SD");
                Long employee = scopedEmployee(warehouse);
                disableWarehouse(warehouse);

                assertThat(as(employee, Set.of(), this::selectorCodes)).isEmpty();
                // 停用只把它移出「可下单的仓」，没有移出授权：管理页仍能读到它，否则没法把它再启用
                assertThat(as(employee, Set.of(), () -> queryIds(null))).contains(warehouse);
            }

            @Test
            @DisplayName("管理页分页同样收窄；表单里的编码筛选只能缩小、不能绕过范围")
            void queryPageIsNarrowedAndUserFilterOnlyShrinks() {
                Long warehouseA = newWarehouse("QA");
                Long warehouseB = newWarehouse("QB");
                Long onlyA = scopedEmployee(warehouseA);

                assertThat(as(onlyA, Set.of(), () -> queryIds(null))).contains(warehouseA);
                // 指名检索那个没授权的仓：结果为空，而不是「范围外的那条也带出来」
                assertThat(as(onlyA, Set.of(), () -> queryIds(warehouseCode(warehouseB)))).isEmpty();
                assertThat(as(onlyA, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM),
                        () -> queryIds(warehouseCode(warehouseB)))).containsExactly(warehouseB);
            }

            @Test
            @DisplayName("详情：未授权按无权限回答（不是不存在），不存在仍是 40485，两种答案不得互换")
            void detailDistinguishesUnauthorizedFromMissing() {
                Long warehouseA = newWarehouse("DA");
                Long warehouseB = newWarehouse("DB");
                Long onlyA = scopedEmployee(warehouseA);

                assertThat(as(onlyA, Set.of(), () -> warehouseQueryService.detail(warehouseA).getId())).isEqualTo(warehouseA);
                assertNoPermission(() -> as(onlyA, Set.of(), () -> warehouseQueryService.detail(warehouseB)));
                expectCode(() -> as(onlyA, Set.of(), () -> warehouseQueryService.detail(-1L)), 40485);
                assertThat(as(onlyA, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM),
                        () -> warehouseQueryService.detail(warehouseB).getId())).isEqualTo(warehouseB);
            }

            @Test
            @DisplayName("无登录上下文：选择器与管理页都返回空，不回退成全量")
            void missingIdentityFailsClosed() {
                newWarehouse("FC");
                XsyRequestUtil.remove();
                assertThat(warehouseQueryService.list()).isEmpty();

                WarehouseQueryForm form = new WarehouseQueryForm();
                form.setPageNum(1L);
                form.setPageSize(20L);
                PageResult<WarehouseVO> page = warehouseQueryService.query(form);
                assertThat(page.getList()).isEmpty();
                assertThat(page.getEmptyFlag()).as("空分页形状与正常分页一致，前端不必另写分支").isTrue();
            }

            @Test
            @DisplayName("失去仓库授权不影响历史单据：采购单读的是行上的当时的仓库名称")
            void historicalOrderStillShowsWarehouseSnapshot() {
                Long warehouse = newWarehouse("HS");
                Long employee = newEmployee("HS1");
                Long skuId = newOnShelfSku("HS");
                Long supplierId = newPurchasableSupplier("HS", skuId);
                // 建单时他是这个仓的授权人（归属由服务端强制为调用者本人）
                grantWarehouseScope(employee, warehouse);
                Long orderId = as(employee, Set.of(), () -> {
                    PurchaseOrderAddForm form = orderForm(supplierId, warehouse, skuId, "4.0000", "6.2000");
                    return purchaseOrderService.create(form, prefix + ":hs:po").getId();
                });
                String snapshotName = warehouseName(warehouse);
                assertThat(as(employee, Set.of(), () -> purchaseQueryService.orderDetail(orderId).getWarehouseName()))
                        .isEqualTo(snapshotName);

                // 收回授权：选择器立刻空掉，但那张历史单上的仓库名仍然读得到 —— 历史记录不是查询授权
                jdbc.update("DELETE FROM xsy_employee_warehouse_scope WHERE employee_id = ?", employee);
                evictMybatisCache();
                assertThat(as(employee, Set.of(), this::selectorCodes)).isEmpty();
                PurchaseOrderVO order = as(employee, Set.of(), () -> purchaseQueryService.orderDetail(orderId));
                assertThat(order.getWarehouseName()).isEqualTo(snapshotName);
                assertThat(order.getWarehouseId()).isEqualTo(warehouse);
            }

            // ==================== 夹具 ====================

            private Long newEmployee(String suffix) {
                String loginName = (prefix + "-" + suffix).toUpperCase(Locale.ROOT);
                jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                                + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                        UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder",
                        "仓库范围" + suffix);
                Long employeeId = jdbc.queryForObject(
                        "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
                assertThat(jdbc.queryForObject(
                        "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                        .as("超管通过不构成权限证据（裁决第 5 条）").isFalse();
                return employeeId;
            }

            private Long scopedEmployee(Long warehouseId) {
                Long employeeId = newEmployee("W" + warehouseId);
                grantWarehouseScope(employeeId, warehouseId);
                return employeeId;
            }

            private void grantWarehouseScope(Long employeeId, Long warehouseId) {
                jdbc.update("INSERT INTO xsy_employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
                        employeeId, warehouseId);
            }

            private void loginAs(Long employeeId) {
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("仓库可见性 IT");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                employee.setDepartmentId(1L);
                XsyRequestUtil.setRequestUser(employee);
            }

            /** 以某个员工 + 一组功能权限执行一段逻辑；不可嵌套（同一线程只能注册一次静态 mock）。 */
            private <T> T as(Long employeeId, Set<String> permissions, Supplier<T> body) {
                loginAs(employeeId);
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    permissions.forEach(p -> stp.when(() -> StpUtil.hasPermission(p)).thenReturn(true));
                    return body.get();
                }
            }

            private List<Long> selectorCodes() {
                return warehouseQueryService.list().stream().map(WarehouseVO::getId).toList();
            }

            /** {@code warehouseCode} 是页面上的一个普通筛选值：它只允许缩小授权范围，不允许绕过。 */
            private List<Long> queryIds(String warehouseCode) {
                WarehouseQueryForm form = new WarehouseQueryForm();
                form.setPageNum(1L);
                form.setPageSize(100L);
                form.setWarehouseCode(warehouseCode);
                return warehouseQueryService.query(form).getList().stream().map(WarehouseVO::getId).toList();
            }

            private String warehouseCode(Long warehouseId) {
                return jdbc.queryForObject("SELECT warehouse_code FROM xsy_warehouse WHERE id = ?",
                        String.class, warehouseId);
            }

            private String warehouseName(Long warehouseId) {
                return jdbc.queryForObject("SELECT name FROM xsy_warehouse WHERE id = ?", String.class, warehouseId);
            }

            private void assertNoPermission(Runnable action) {
                assertThatThrownBy(action::run)
                        .isInstanceOf(BusinessException.class)
                        .isNotInstanceOf(ScmBusinessException.class)
                        .hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
            }
        }
    }

    @Nested
    @WebMvcTest(WarehouseController.class)
    @AutoConfigureMockMvc(addFilters = false)
    @ContextConfiguration(classes = {WarehouseController.class, ScmExceptionHandler.class, GlobalExceptionHandler.class})
    class WarehouseWebTest {
        @Autowired
        private MockMvc mvc;

        @MockitoBean
        private WarehouseService service;

        @MockitoBean
        private WarehouseQueryService queryService;

        @MockitoBean(name = "systemEnvironment")
        private SystemEnvironment environment;

        @Test
        @DisplayName("GET /list 返回数组信封（下拉选择器）")
        void returnsListEnvelope() throws Exception {
            WarehouseVO vo = new WarehouseVO();
            vo.setId(1L);
            vo.setWarehouseCode("WH001");
            vo.setName("默认仓库");
            vo.setStatus("ENABLED");
            when(queryService.list()).thenReturn(List.of(vo));

            mvc.perform(get("/scm/warehouse/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].warehouseCode").value("WH001"));
        }

        @Test
        @DisplayName("POST /query 返回分页信封")
        void returnsPageEnvelope() throws Exception {
            PageResult<WarehouseVO> page = new PageResult<>();
            page.setPageNum(1L);
            page.setPageSize(20L);
            page.setTotal(0L);
            page.setPages(0L);
            page.setList(List.of());
            page.setEmptyFlag(true);
            when(queryService.query(any())).thenReturn(page);

            mvc.perform(post("/scm/warehouse/query").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pageNum\":1,\"pageSize\":20}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.list").isArray());
        }

        @Test
        @DisplayName("GET /detail/{id} 返回单个对象")
        void returnsDetail() throws Exception {
            WarehouseVO vo = new WarehouseVO();
            vo.setId(9L);
            vo.setWarehouseCode("WH009");
            when(queryService.detail(9L)).thenReturn(vo);

            mvc.perform(get("/scm/warehouse/detail/9"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(9));
        }

        @Test
        @DisplayName("POST /create 返回新仓库 id")
        void createsWarehouse() throws Exception {
            when(service.create(any())).thenReturn(42L);

            mvc.perform(post("/scm/warehouse/create").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"warehouseCode\":\"WH002\",\"name\":\"二号仓\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(42));
        }

        @Test
        @DisplayName("POST /update 成功返回空信封")
        void updatesWarehouse() throws Exception {
            mvc.perform(post("/scm/warehouse/update").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":1,\"version\":0,\"warehouseCode\":\"WH001\",\"name\":\"默认仓库\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("仓库不存在 → 40485（HTTP 200 + 业务码）")
        void mapsNotFound() throws Exception {
            doThrow(new ScmBusinessException(WarehouseErrorCode.WAREHOUSE_NOT_FOUND)).when(service).update(any());

            mvc.perform(post("/scm/warehouse/update").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":1,\"version\":0,\"warehouseCode\":\"WH001\",\"name\":\"默认仓库\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40485));
        }

        @Test
        @DisplayName("编码重复 → 40996")
        void mapsDuplicateCode() throws Exception {
            doThrow(new ScmBusinessException(WarehouseErrorCode.WAREHOUSE_CODE_DUPLICATE)).when(service).create(any());

            mvc.perform(post("/scm/warehouse/create").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"warehouseCode\":\"WH001\",\"name\":\"默认仓库\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40996));
        }

        @Test
        @DisplayName("乐观锁冲突 → 40921")
        void mapsVersionConflict() throws Exception {
            doThrow(new ScmBusinessException(VERSION_CONFLICT)).when(service).update(any());

            mvc.perform(post("/scm/warehouse/update").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":1,\"version\":7,\"warehouseCode\":\"WH001\",\"name\":\"默认仓库\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40921));
        }

        @Test
        @DisplayName("MVC 校验：编码不再必填（服务端生成）；名称缺失 / 全空白 → 30001")
        void validatesForm() throws Exception {
            // 不提交编码、名称合法 → 通过 MVC 校验到达 service
            mvc.perform(post("/scm/warehouse/create").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"二号仓\"}"))
                    .andExpect(jsonPath("$.code").value(0));

            mvc.perform(post("/scm/warehouse/create").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"   \"}"))
                    .andExpect(jsonPath("$.code").value(30001));

            mvc.perform(post("/scm/warehouse/create").contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(jsonPath("$.code").value(30001));
        }

        @Test
        @DisplayName("查询条件状态取值域受限：非法 status → 30001")
        void validatesQueryStatus() throws Exception {
            mvc.perform(post("/scm/warehouse/query").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pageNum\":1,\"pageSize\":20,\"status\":\"BROKEN\"}"))
                    .andExpect(jsonPath("$.code").value(30001));
        }
    }
}
