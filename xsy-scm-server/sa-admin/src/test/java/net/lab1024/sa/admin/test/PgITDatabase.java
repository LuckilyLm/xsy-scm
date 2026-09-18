package net.lab1024.sa.admin.test;

/**
 * 集成测试数据库连接参数。
 *
 * <p><b>背景：</b>历史基线把这几个 migration / 锁相关的 IT 的 JDBC URL 直接写死为
 * {@code jdbc:postgresql://127.0.0.1:15432/xsy_scm?currentSchema=xsy_v2}。
 * 而 {@code xsy_scm} 是**已弃用的存量库**（停在 version 17 且与仓库分叉），
 * 于是这些用例会以 {@code FlywayValidateException: Migration checksum mismatch for version 17}
 * 失败 —— 失败原因是环境，不是业务代码。
 *
 * <p><b>取值约定：</b>与 {@code sa-base.yaml} 使用同一个环境变量 {@code XSY_V2_DB_URL}，
 * 未设置时回退到 {@link #DEFAULT_URL}，保证既有调用方行为完全不变。
 * 注意主配置里的 URL 带 {@code jdbc:p6spy:} 前缀（driver 为 P6SpyDriver），
 * 而这里是 {@code DriverManager} / {@code Flyway} / {@code PGSimpleDataSource} 裸连接，
 * 因此会把该前缀剥掉。
 *
 * <p><b>怎么跑这些 IT：</b>指向一个**干净库**（只有 schema 与系统基线，没有开发种子数据），
 * 例如：
 * <pre>
 * export XSY_V2_DB_URL="jdbc:p6spy:postgresql://127.0.0.1:15432/&lt;干净库&gt;?currentSchema=xsy_v2"
 * </pre>
 * 用带开发种子数据的库跑，会因分类树等既有数据与用例假设冲突而报「上级分类不正确」。
 */
public interface PgITDatabase {

    /** 未显式指定时使用的默认地址（保留原始值，避免改变既有行为）。 */
    String DEFAULT_URL = "jdbc:postgresql://127.0.0.1:15432/xsy_scm?currentSchema=xsy_v2";

    /** 裸 JDBC 连接地址：优先取 {@code XSY_V2_DB_URL}，并剥掉 p6spy 前缀。 */
    static String url() {
        String value = System.getenv("XSY_V2_DB_URL");
        if (value == null || value.isBlank()) {
            return DEFAULT_URL;
        }
        return value.replace("jdbc:p6spy:", "jdbc:");
    }

    /** 数据库用户名。 */
    static String user() {
        return System.getenv().getOrDefault("XSY_V2_DB_USERNAME", "xsy_scm_app");
    }

    /** 数据库口令。 */
    static String password() {
        return System.getenv("XSY_V2_DB_PASSWORD");
    }
}
