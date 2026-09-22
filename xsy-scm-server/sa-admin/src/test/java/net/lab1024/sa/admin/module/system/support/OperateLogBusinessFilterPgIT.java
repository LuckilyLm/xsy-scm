package net.lab1024.sa.admin.module.system.support;

import net.lab1024.sa.admin.AdminApplication;
import net.lab1024.sa.admin.test.PgITPaths;
import net.lab1024.sa.base.module.support.operatelog.OperateLogService;
import net.lab1024.sa.base.module.support.operatelog.domain.OperateLogQueryForm;
import net.lab1024.sa.base.module.support.operatelog.domain.OperateLogVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 操作日志「结构化业务对象筛选」在真实 PostgreSQL 上的行为验证。
 *
 * <p>筛选走 {@code OperateLogMapper.xml} 的 STRPOS 精确匹配，其正确性依赖 PostgreSQL 的字符串语义
 * 与分页插件的「先过滤、后 count」，只有真实库才能验证，故用 IT 而非 mock。
 *
 * <p>每个用例用独立 {@link #OWNER} 作为 operate_user_id 隔离既有数据，并用 operate_user_name 当行标签：
 * 断言比对返回的标签集合，而不是自增主键，避免依赖发号顺序。事务回滚，不落库。
 */
@SpringBootTest(classes = AdminApplication.class, properties = {
        "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
        "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
        "file.storage.local.url-prefix=http://127.0.0.1:18082",
        "logging.level.root=WARN"})
@Transactional
class OperateLogBusinessFilterPgIT {

    /** 与其它测试/既有数据隔离的操作人哨兵值，真实员工 id 都是个位数级别。 */
    private static final long OWNER = 88_880_001L;
    private static final int USER_TYPE = 1;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OperateLogService operateLogService;

    private void log(String tag, String url, String param) {
        jdbc.update("INSERT INTO t_operate_log (operate_user_id, operate_user_type, operate_user_name, url, method, param, success_flag)"
                        + " VALUES (?,?,?,?,?,?,?)",
                OWNER, USER_TYPE, tag, url, "Svc.method", param, true);
    }

    /** 仅设置业务筛选与隔离条件后查询，返回命中行的标签集合。 */
    private Set<String> tagsOf(String businessType, long businessId) {
        return list(queryForm(businessType, businessId, 100L)).stream()
                .map(OperateLogVO::getOperateUserName)
                .collect(Collectors.toSet());
    }

    private List<OperateLogVO> list(OperateLogQueryForm form) {
        return operateLogService.queryByPage(form).getData().getList();
    }

    private OperateLogQueryForm queryForm(String businessType, long businessId, long pageSize) {
        OperateLogQueryForm form = new OperateLogQueryForm();
        form.setOperateUserId(OWNER);
        form.setOperateUserType(USER_TYPE);
        form.setBusinessType(businessType);
        form.setBusinessId(businessId);
        form.setPageNum(1L);
        form.setPageSize(pageSize);
        form.setSearchCount(true);
        return form;
    }

    @Test
    void productFilterMatchesExactSpuIdBoundaryNotSiblingsOrSubResource() {
        log("spu12", "/scm/product/spu/update", "[{\"spuId\":12,\"qty\":5}]");
        log("spu12tail", "/scm/product/spu/delete", "[{\"spuId\":12}]");
        log("spu112", "/scm/product/spu/update", "[{\"spuId\":112,\"qty\":5}]");
        log("sku12", "/scm/product/sku/update", "[{\"skuId\":12,\"qty\":5}]");

        // ID 12 只命中真正的 spuId=12（含作为末字段的 }），不误命中 spuId=112 与子资源 skuId=12
        assertThat(tagsOf("PRODUCT", 12L)).containsExactlyInAnyOrder("spu12", "spu12tail");
        assertThat(tagsOf("PRODUCT", 112L)).containsExactly("spu112");
    }

    @Test
    void customerFilterSeparatesCustomerIdFromParentAndLongerId() {
        log("cus12", "/scm/customer/update", "[{\"customerId\":12,\"customerTypeId\":9}]");
        log("parentCus12", "/scm/customer/add", "[{\"parentCustomerId\":12,\"status\":\"ACTIVE\"}]");
        log("cus123", "/scm/customer/delete", "[{\"customerId\":123}]");

        // 前导引号使 parentCustomerId 不被当作 customerId；数字边界使 12 不命中 123
        assertThat(tagsOf("CUSTOMER", 12L)).containsExactly("cus12");
        assertThat(tagsOf("CUSTOMER", 123L)).containsExactly("cus123");
    }

    @Test
    void deliveryRouteFilterMatchesWholeUrlPathSegment() {
        log("r12plan", "/scm/delivery/routes/12/plan", "[12,{\"version\":1}]");
        log("r12update", "/scm/delivery/routes/12", "[12,{\"name\":\"x\"}]");
        log("r123plan", "/scm/delivery/routes/123/plan", "[123,{\"version\":1}]");

        // 线路 id 是 @PathVariable，落在 url 段里；按完整段匹配，12 不误命中 123
        assertThat(tagsOf("DELIVERY_ROUTE", 12L)).containsExactlyInAnyOrder("r12plan", "r12update");
        assertThat(tagsOf("DELIVERY_ROUTE", 123L)).containsExactly("r123plan");
    }

    @Test
    void malformedOrMissingParamFailsClosedWithoutError() {
        log("truncated", "/scm/product/spu/update", "[{\"spuId\":12");
        log("nullParam", "/scm/product/spu/update", null);
        log("gibberish", "/scm/product/spu/update", "spuId:12 not-json");

        // 脏值/缺列时 STRPOS 只返回 0 或 NULL，查询正常返回且不误归因（fail closed）
        assertThat(tagsOf("PRODUCT", 12L)).isEmpty();
    }

    @Test
    void createWithoutRecoverableIdIsNotAttributable() {
        // 新增只回 ResponseDTO<Long>，切面把 data 强制置空，因此新对象 id 无法从日志还原
        log("routeCreate", "/scm/delivery/routes", "[{\"routeName\":\"new\"}]");
        log("productAdd", "/scm/product/spu/add", "[{\"spuCode\":\"NEW\",\"name\":\"n\"}]");

        assertThat(tagsOf("DELIVERY_ROUTE", 12L)).isEmpty();
        assertThat(tagsOf("PRODUCT", 12L)).isEmpty();
    }

    @Test
    void businessFilterAppliedBeforePaginationAndTotalConsistent() {
        log("pg-a", "/scm/product/spu/update", "[{\"spuId\":777,\"q\":1}]");
        log("pg-b", "/scm/product/spu/update", "[{\"spuId\":777,\"q\":2}]");
        log("pg-c", "/scm/product/spu/update", "[{\"spuId\":777,\"q\":3}]");
        log("pg-7777", "/scm/product/spu/update", "[{\"spuId\":7777,\"q\":9}]");

        // total 统计的是过滤后的命中数（3），而非全库或分页大小；列表按 pageSize 截断
        var result = operateLogService.queryByPage(queryForm("PRODUCT", 777L, 2L)).getData();
        assertThat(result.getTotal()).isEqualTo(3L);
        assertThat(result.getList()).hasSize(2);
        assertThat(result.getList()).extracting(OperateLogVO::getOperateUserName)
                .allMatch(t -> t.startsWith("pg-") && !t.equals("pg-7777"));
    }
}
