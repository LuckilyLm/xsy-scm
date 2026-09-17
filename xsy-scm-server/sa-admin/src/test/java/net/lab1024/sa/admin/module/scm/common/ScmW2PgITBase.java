package net.lab1024.sa.admin.module.scm.common;


import net.lab1024.sa.admin.test.PgITPaths;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.lab1024.sa.admin.AdminApplication;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSkuForm;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSpuAddForm;
import net.lab1024.sa.admin.module.scm.product.service.ProductSpuService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W2 PostgreSQL 集成测试基类。
 *
 * <p>W2 的读写在数据库里才有完整语义（partial unique index、CHECK 约束、{@code FOR UPDATE} 锁序、
 * {@code JSONB} 映射、{@code NUMERIC(18,4)} 精度），因此这部分只能用真实 PostgreSQL 验证。
 *
 * <p><b>事务策略：</b>与 W1 {@code ProductPgIT} 一致——整个用例包在一个事务里，结束时回滚，
 * 不向开发库留下任何数据。种子数据（{@code customer_type}）由 V8 migration 提供，只读使用。
 *
 * <p><b>不 mock 商品域：</b>{@code supplier_sku} 的外键语义依赖真实存在的 {@code product_spu} /
 * {@code product_sku}，所以这里通过 W1 已验收的 {@link ProductSpuService} 造数，而不是直接插表——
 * 直接插表会绕过 W1 的聚合不变量，让 IT 验证到一份「现实中不可能出现」的商品数据。
 */
@SpringBootTest(classes = AdminApplication.class, properties = {
        "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
        "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
        "file.storage.local.url-prefix=http://127.0.0.1:18082",
        "logging.level.root=WARN"})
@Transactional
public abstract class ScmW2PgITBase {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected ProductSpuService productSpuService;

    /** 每个用例独立的编码前缀，避免与其它用例或既有数据撞 partial unique index。 */
    protected String prefix;

    @BeforeEach
    void setUpOperator() {
        prefix = "W2-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("W2 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        SmartRequestUtil.setRequestUser(employee);
    }

    @AfterEach
    void tearDownOperator() {
        SmartRequestUtil.remove();
    }

    // ------------------------------------------------------------------
    // 断言辅助
    // ------------------------------------------------------------------

    /** 断言动作抛出带指定业务码的 {@link ScmBusinessException}。 */
    protected static void expectCode(Runnable action, int code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
    }

    // ------------------------------------------------------------------
    // 数据辅助
    // ------------------------------------------------------------------

    protected Long customerTypeId(String typeCode) {
        return jdbc.queryForObject(
                "SELECT id FROM customer_type WHERE type_code = ? AND deleted = FALSE", Long.class, typeCode);
    }

    /** 任一可用员工 id，用于业务员 / 默认采购员引用。 */
    protected Long anyEmployeeId() {
        return jdbc.queryForObject(
                "SELECT employee_id FROM t_employee WHERE deleted_flag = FALSE ORDER BY employee_id LIMIT 1",
                Long.class);
    }

    protected Long newOnShelfSku(String suffix) {
        return newProductSku(suffix, "ON_SHELF", "ON_SHELF");
    }

    /**
     * 用 W1 已验收的商品聚合服务造一个真实 SKU，返回 {@code product_sku.id}。
     *
     * @param spuStatus SPU 上下架状态（{@code ON_SHELF} / {@code OFF_SHELF}）
     * @param skuStatus SKU 上下架状态
     */
    protected Long newProductSku(String suffix, String spuStatus, String skuStatus) {
        ProductSpuAddForm form = new ProductSpuAddForm();
        form.setSpuCode(prefix + suffix);
        form.setName(prefix + suffix + "商品");
        form.setStatus(spuStatus);
        form.setCategoryId(jdbc.queryForObject(
                "SELECT id FROM product_category WHERE category_code = 'FRESH-FRUIT' AND deleted = FALSE", Long.class));

        ProductSkuForm sku = new ProductSkuForm();
        sku.setSkuCode(prefix + suffix + "-K");
        sku.setSpecName("规格" + suffix);
        sku.setSpecValues(new LinkedHashMap<>(Map.of("规格", suffix)));
        sku.setSaleUnit("kg");
        sku.setProductType("NON_STANDARD");
        sku.setMarketPrice(new BigDecimal("1.2000"));
        sku.setStatus(skuStatus);
        sku.setDefaultFlag(true);
        form.setSkuList(new ArrayList<>(List.of(sku)));

        Long spuId = productSpuService.add(form);
        return jdbc.queryForObject(
                "SELECT id FROM product_sku WHERE spu_id = ? AND deleted = FALSE", Long.class, spuId);
    }
}
