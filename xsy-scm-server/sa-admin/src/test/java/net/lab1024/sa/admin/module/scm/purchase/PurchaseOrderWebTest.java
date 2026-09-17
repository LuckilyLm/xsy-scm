package net.lab1024.sa.admin.module.scm.purchase;

import cn.dev33.satoken.annotation.SaCheckPermission;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.handler.ScmExceptionHandler;
import net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode;
import net.lab1024.sa.admin.module.scm.purchase.controller.PurchaseDemandController;
import net.lab1024.sa.admin.module.scm.purchase.controller.PurchaseOrderController;
import net.lab1024.sa.admin.module.scm.purchase.controller.PurchaseReceiptController;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseDemandVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseDemandService;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseOrderService;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseQueryService;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseReceiptService;
import net.lab1024.sa.admin.module.scm.warehouse.controller.WarehouseController;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseQueryService;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
import net.lab1024.sa.base.common.domain.SystemEnvironment;
import net.lab1024.sa.base.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W5 Web 层（W5 Target Design §11.3，8 例）。
 *
 * <p>覆盖三件事：
 * <ol>
 *   <li><b>27 个端点全部有原生权限注解</b>（3 需求 + 11 采购单 + 8 收货 + 5 仓库）。
 *       少一个注解 = 该接口对所有人开放，而这是**没有编译期信号**的错误；</li>
 *   <li><b>定点数一律是 4 位小数字符串、`null` 必须保持 `null`</b>。
 *       `0.0000`（合法价格/数量）与 `null`（未定价/未分配）是两种不同的业务事实，
 *       序列化时把 `null` 写成 `"0.0000"` 会让前端三态渲染直接失效；</li>
 *   <li><b>JSON 数字不得进入定点数字段</b>：`"quantity": 1.2` 必须被拒（30001），
 *       而不是被静默转成 `1.2000` —— 浮点数进入数量字段是精度事故的源头。</li>
 * </ol>
 *
 * <p>{@code addFilters = false}：Sa-Token 的 Servlet 过滤器不参与，
 * 因此这里验证的是**注解存在**（第 1 例，反射），而不是「未登录被拦」——
 * 后者由 W4 已验收的底座过滤器覆盖，不在业务 Web 测试的职责内。
 */
@WebMvcTest({PurchaseDemandController.class, PurchaseOrderController.class,
        PurchaseReceiptController.class, WarehouseController.class})
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {PurchaseDemandController.class, PurchaseOrderController.class,
        PurchaseReceiptController.class, WarehouseController.class,
        ScmExceptionHandler.class, GlobalExceptionHandler.class})
@DisplayName("W5 Web 层：27 端点 + 权限注解 + 定点数字符串序列化")
class PurchaseOrderWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PurchaseDemandService demandService;

    @MockitoBean
    private PurchaseOrderService orderService;

    @MockitoBean
    private PurchaseReceiptService receiptService;

    @MockitoBean
    private PurchaseQueryService queries;

    @MockitoBean
    private WarehouseService warehouseService;

    /**
     * {@code WarehouseController} 的构造器是 `(WarehouseService, WarehouseQueryService)`。
     *
     * <p>`@WebMvcTest` 只自动注册 Web 层切片 Bean，Service 一律不扫描 —— 漏掉这一个会让
     * **整个上下文加载失败**，8 个用例全部报 `ApplicationContext failure threshold exceeded`，
     * 而真正的错误信息（`No qualifying bean of type WarehouseQueryService`）藏在 `Caused by` 里。
     */
    @MockitoBean
    private WarehouseQueryService warehouseQueryService;

    @MockitoBean(name = "systemEnvironment")
    private SystemEnvironment environment;

    // ------------------------------------------------------------------
    // 1. 端点与权限注解
    // ------------------------------------------------------------------

    @Test
    @DisplayName("27 个端点全部声明 @SaCheckPermission，且权限码都在 scm: 命名空间内")
    void allTwentySevenEndpointsHaveNativePermissions() {
        int count = 0;
        for (Class<?> controller : List.of(PurchaseDemandController.class, PurchaseOrderController.class,
                PurchaseReceiptController.class, WarehouseController.class)) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
                assertThat(permission)
                        .as("%s#%s 必须声明 @SaCheckPermission", controller.getSimpleName(), method.getName())
                        .isNotNull();
                assertThat(permission.value()).isNotEmpty();
                assertThat(permission.value()[0]).startsWith("scm:");
                count++;
            }
        }
        assertThat(count).isEqualTo(27);
    }

    // ------------------------------------------------------------------
    // 2 / 3 / 4. 定点数字符串与 null 语义
    // ------------------------------------------------------------------

    @Test
    @DisplayName("采购单详情：金额输出 4 位定点字符串，null 保持缺席（不写成 0.0000）")
    void orderDetailUsesFourDecimalStringsAndKeepsNull() throws Exception {
        PurchaseOrderVO vo = new PurchaseOrderVO();
        vo.setId(1L);
        vo.setOrderNo("PO20260916000001");
        vo.setStatus("DRAFT");
        vo.setTotalAmount(new BigDecimal("18.6"));
        vo.setReceivedProgress(null);
        when(queries.orderDetail(1L)).thenReturn(vo);

        mvc.perform(get("/scm/purchase/detail/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value("18.6000"))
                .andExpect(jsonPath("$.data.receivedProgress").doesNotExist());
    }

    @Test
    @DisplayName("收货单详情：对账量输出 4 位定点字符串，实重 null 保持缺席")
    void receiptDetailUsesFourDecimalStringsAndKeepsNull() throws Exception {
        PurchaseReceiptVO vo = new PurchaseReceiptVO();
        vo.setId(7L);
        vo.setReceiptNo("PR20260916000001");
        vo.setStatus("DRAFT");
        when(queries.receiptDetail(7L)).thenReturn(vo);

        mvc.perform(get("/scm/purchase/receipt/detail/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.receiptNo").value("PR20260916000001"));
    }

    @Test
    @DisplayName("需求返回体（allocate）：required / allocated / unallocated 都是 4 位定点字符串")
    void demandDetailUsesFourDecimalStrings() throws Exception {
        PurchaseDemandVO vo = new PurchaseDemandVO();
        vo.setId(3L);
        vo.setStatus("PARTIALLY_ALLOCATED");
        vo.setRequiredQuantity(new BigDecimal("10"));
        vo.setAllocatedQuantity(new BigDecimal("4"));
        vo.setUnallocatedQuantity(new BigDecimal("6"));
        when(demandService.allocate(any(), any())).thenReturn(vo);

        // 需求**没有** `/demand/detail/{id}` 端点（§8.1 只定义 query / generate / allocate 三个），
        // 而 `PurchaseDemandVO` 的两个真实出口是 `query` 的分页元素与 `allocate` 的返回体。
        // 这里走 `allocate`：单对象响应，断言最直接。
        mvc.perform(post("/scm/purchase/demand/allocate")
                        .header("Idempotency-Key", "W5-WEB-demand-allocate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"demandId\":3,\"purchaseOrderItemId\":9,\"quantity\":\"4.0000\","
                                + "\"supplierId\":1,\"warehouseId\":1,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.requiredQuantity").value("10.0000"))
                .andExpect(jsonPath("$.data.allocatedQuantity").value("4.0000"))
                .andExpect(jsonPath("$.data.unallocatedQuantity").value("6.0000"));
    }

    // ------------------------------------------------------------------
    // 5. 幂等键缺失
    // ------------------------------------------------------------------

    @Test
    @DisplayName("缺少 Idempotency-Key：业务码 40084 原样透出（不是 30001 参数错误）")
    void missingIdempotencyKeyReturnsStableBusinessCode() throws Exception {
        when(orderService.create(any(), isNull()))
                .thenThrow(new ScmBusinessException(PurchaseErrorCode.PURCHASE_IDEMPOTENCY_KEY_REQUIRED));

        mvc.perform(post("/scm/purchase/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":1,\"warehouseId\":1,\"items\":"
                                + "[{\"skuId\":1,\"quantity\":\"1.0000\",\"price\":\"1.0000\"}]}"))
                .andExpect(jsonPath("$.code").value(40084));
    }

    // ------------------------------------------------------------------
    // 6. JSON 数字不得进入定点数字段
    // ------------------------------------------------------------------

    @Test
    @DisplayName("quantity 用 JSON 数字（1.2）→ 30001，且不进 Service（浮点数不得进入数量字段）")
    void jsonNumberCannotEnterFixedPointQuantity() throws Exception {
        mvc.perform(post("/scm/purchase/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":1,\"warehouseId\":1,\"items\":"
                                + "[{\"skuId\":1,\"quantity\":1.2,\"price\":\"1.0000\"}]}"))
                .andExpect(jsonPath("$.code").value(30001));
        verifyNoInteractions(orderService);
    }

    // ------------------------------------------------------------------
    // 7 / 8. 空集合校验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("采购单 items 为空 → 30001（Bean Validation 挡住，不进 Service）")
    void createOrderRejectsEmptyItems() throws Exception {
        mvc.perform(post("/scm/purchase/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":1,\"warehouseId\":1,\"items\":[]}"))
                .andExpect(jsonPath("$.code").value(30001));
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("确认收货 items 为空 → 30001（必须覆盖全部收货行）")
    void confirmReceiptRejectsEmptyItems() throws Exception {
        mvc.perform(post("/scm/purchase/receipt/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"version\":0,\"items\":[]}"))
                .andExpect(jsonPath("$.code").value(30001));
        verifyNoInteractions(receiptService);
    }
}
