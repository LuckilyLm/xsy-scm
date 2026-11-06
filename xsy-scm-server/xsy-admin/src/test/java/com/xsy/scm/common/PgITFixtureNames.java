package com.xsy.scm.common;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.admin.test.PgITBaselineDatabase;
import com.xsy.scm.admin.test.PgITDatabase;
import com.xsy.scm.admin.test.PgITMigrationHistory;
import com.xsy.scm.base.config.MybatisPlusConfig;
import com.xsy.scm.common.dao.AppendOnlyMapper;
import com.xsy.scm.common.excel.ScmExcel;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.export.ScmExportColumnSelection.Column;
import com.xsy.scm.common.export.ScmExportColumnSelection;
import com.xsy.scm.common.filter.ScmAdvancedFilterCatalog.Condition;
import com.xsy.scm.common.filter.ScmAdvancedFilterCatalog.Field;
import com.xsy.scm.common.filter.ScmAdvancedFilterCatalog;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.validation.ScmServiceValidationAspect;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.permission.CustomerPermission;
import com.xsy.scm.product.domain.form.ProductSpuQueryForm;
import com.xsy.scm.product.support.ProductAdvancedFilters;
import com.xsy.scm.report.domain.form.ScmPurchaseReportQueryForm;
import com.xsy.scm.report.domain.form.ScmSalesReportQueryForm;
import com.xsy.scm.report.support.ScmReportAdvancedFilters;
import com.xsy.scm.supplier.permission.SupplierPermission;
import jakarta.validation.Validation;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.Data;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


final class PgITFixtureNames {
    /**
     * 品牌名池：覆盖生鲜供应链里常见的自营 / 合作品牌，避免所有夹具都叫同一个名字。
     */
    private static final String[] BRANDS = {"鲜蔬源", "绿源", "禾丰", "优鲜达", "鲜果记", "青禾"};

    /**
     * 操作人姓名池：审计日志里的「操作人」列直接用这些名字。
     */
    private static final String[] OPERATORS = {"张伟", "李静", "王海涛", "陈晓芸", "周敏"};

    /**
     * 本 JVM 运行的唯一短码。
     *
     * <p>计数器每次启动都从 1 开始，只用「品牌 + 序号」时两次测试运行会生成同一批前缀；
     * 防重复处理记录留在库里不清理，第二次运行就会撞上「同键不同内容」。把运行短码拼进前缀，
     * 重跑和并发 fork 都不会复用上一次的键。
     */
    private static final String RUN_CODE = java.util.UUID.randomUUID().toString()
        .substring(0, 4).toUpperCase(java.util.Locale.ROOT);

    private static final AtomicInteger BRAND_SEQ = new AtomicInteger();

    private static final AtomicInteger OPERATOR_SEQ = new AtomicInteger();

    private PgITFixtureNames() {
    }

    /**
     * 本次用例的品牌前缀，形如 {@code 绿源137}。
     *
     * <p>调用方习惯拼成「前缀 + 商品 / 仓 / 线路 / 客户」，因此这里只给品牌 + 号段，
     * 拼出来就是「绿源137仓」「绿源137商品」这类内部叫法。
     */
    static String brand() {
        int n = BRAND_SEQ.incrementAndGet();
        // 后缀不能再取模：模 900 时整个 JVM 每 900 个用例就会复用同一前缀，
        // 防重复请求编号（前缀 + 序号）随之撞车 → 40990「相同防重复请求编号的请求内容不一致」。
        // 再拼上本次运行的短码，避免「重跑一次测试」复用上次留在库里的键。
        return BRANDS[n % BRANDS.length] + (100 + n) + RUN_CODE;
    }

    /**
     * 本次用例的操作人姓名。
     */
    static String operator() {
        int n = OPERATOR_SEQ.incrementAndGet();
        return OPERATORS[n % OPERATORS.length];
    }
}
