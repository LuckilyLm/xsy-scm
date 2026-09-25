package net.lab1024.sa.admin.module.scm.finance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 财务域对业务域**只读**的静态契约（设计稿 §0 第 2 条、全局不变量 4，F1-1 交付物）。
 *
 * <p>财务域是新边界：既有跨域写都走显式契约（如 {@code PurchaseInventoryContract}），
 * 而财务域被允许直接建只读 DAO 去读订单 / 退货 / 收货 / 出库 / 配送表。允许只读的代价是
 * 「不小心写了一句」没有任何编译期或运行期信号 —— 页面照常跑、接口照常返回成功，
 * 只有库存账或订单账在事后解释不了。因此把边界钉成一个静态扫描断言。
 *
 * <p><b>为什么是静态扫描而不是运行时拦截</b>：运行时只能抓到「被执行到的那条写语句」，
 * 而 F1-1 的写路径一条都还没有；等到 F1-2 接上生成器再补断言，恰好错过了唯一一次
 * 「新增代码是否越界」的评审时机。静态扫描在编译产物之外检查源码文本，
 * 新增任何一句越界 SQL 都会当场失败。
 *
 * <p><b>不是 Spring 测试</b>：只读源码文件，因此跑得比 IT 快得多，也不依赖数据库。
 */
@DisplayName("财务域只读契约（静态扫描）")
class FinanceReadOnlyContractTest {

    /**
     * 财务域不得写入的业务表，按前缀匹配（指令口径：{@code sales_order*} / {@code order_return*} /
     * {@code order_refund} / {@code purchase_*} / {@code inventory_*} / {@code delivery_*}）。
     *
     * <p>{@code sorting_task} 也一并纳入：财务不消费分拣事实（应收数量只取出库量，Q2），
     * 因此同样没有任何写它的理由。
     */
    private static final List<String> READ_ONLY_TABLE_PREFIXES = List.of(
            "sales_order", "order_return", "order_refund",
            "purchase_", "inventory_", "delivery_", "sorting_");

    /**
     * 写语句关键字。刻意不含 {@code SELECT}：财务域**允许**读这些表，这正是本契约的前提。
     */
    private static final Pattern WRITE_STATEMENT = Pattern.compile(
            "\\b(INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|TRUNCATE(?:\\s+TABLE)?)\\s+"
                    + "(" + String.join("|", READ_ONLY_TABLE_PREFIXES) + ")[A-Za-z0-9_]*",
            Pattern.CASE_INSENSITIVE);

    /**
     * Java 字符串字面量（含文本块）。SQL 只可能出现在这里或 mapper XML 里，
     * 因此只扫描这两处就能覆盖全部越界写法，同时避免把 javadoc 里
     * 「不得 UPDATE {@code sales_order}」这类**说明性文字**误判成违规。
     */
    private static final Pattern JAVA_TEXT_BLOCK = Pattern.compile("\"\"\"(.*?)\"\"\"", Pattern.DOTALL);
    private static final Pattern JAVA_STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static final Pattern SQL_ANNOTATION = Pattern.compile(
            "@(Insert|Update|Delete|Select)\\s*\\(");

    private static final Pattern TABLE_NAME_ANNOTATION = Pattern.compile("@TableName\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"");

    private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    @Test
    @DisplayName("finance 包内不存在针对业务表 / 库存表 / 配送表 / 分拣表的写语句")
    void financePackageNeverWritesBusinessTables() throws IOException {
        Path javaRoot = moduleRoot().resolve("src/main/java/net/lab1024/sa/admin/module/scm/finance");
        assertThat(javaRoot).as("财务域源码目录必须存在，否则本断言形同虚设").exists();

        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(javaRoot)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path, StandardCharsets.UTF_8);
                for (String fragment : stringFragmentsOf(source)) {
                    collectViolations(fragment, javaRoot.relativize(path).toString(), violations);
                }
            }
        }

        // mapper XML：财务域自定义 SQL 的唯一合法去处（AGENTS.md §8）。F1-1 还没有，
        // 但目录一旦出现就要纳入扫描，否则越界 SQL 可以从这里溜过去。
        Path mapperRoot = moduleRoot().resolve("src/main/resources/mapper/scm/finance");
        if (Files.isDirectory(mapperRoot)) {
            try (Stream<Path> paths = Files.walk(mapperRoot)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".xml")).toList()) {
                    String xml = XML_COMMENT.matcher(Files.readString(path, StandardCharsets.UTF_8))
                            .replaceAll(" ");
                    collectViolations(xml, mapperRoot.relativize(path).toString(), violations);
                }
            }
        }

        assertThat(violations)
                .as("财务域对业务表只读；越界写语句会让库存账 / 订单账出现解释不了的事实")
                .isEmpty();
    }

    @Test
    @DisplayName("finance 包不在 mapper 注解里写 SQL（AGENTS.md §8）")
    void financePackageHasNoSqlInAnnotations() throws IOException {
        Path javaRoot = moduleRoot().resolve("src/main/java/net/lab1024/sa/admin/module/scm/finance");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(javaRoot)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path, StandardCharsets.UTF_8);
                Matcher matcher = SQL_ANNOTATION.matcher(source);
                while (matcher.find()) {
                    violations.add(javaRoot.relativize(path) + " -> @" + matcher.group(1));
                }
            }
        }
        assertThat(violations)
                .as("自定义 SQL 必须放 mapper XML，注解里只留方法签名")
                .isEmpty();
    }

    @Test
    @DisplayName("finance 包的每个实体都映射 finance_* 表，因此 BaseMapper 的写方法够不到业务表")
    void everyFinanceEntityMapsAFinanceTable() throws IOException {
        Path javaRoot = moduleRoot().resolve("src/main/java/net/lab1024/sa/admin/module/scm/finance");
        List<String> mapped = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(javaRoot)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = TABLE_NAME_ANNOTATION.matcher(Files.readString(path, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    mapped.add(matcher.group(1));
                }
            }
        }
        // 八张表都必须有实体，否则「实体 → 表」这条最强的边界证据就缺了一块。
        assertThat(mapped)
                .as("财务域实体的 @TableName 取值")
                .hasSize(8)
                .allSatisfy(table -> assertThat(table).startsWith("finance_"))
                .contains("finance_receivable", "finance_receivable_item",
                        "finance_payable", "finance_payable_item",
                        "finance_receipt", "finance_payment",
                        "finance_write_off", "finance_operation_log");
    }

    @Test
    @DisplayName("finance 包不依赖 report 包（设计稿 §18：导出层自建 FinanceExcel，不跨域引用）")
    void financePackageDoesNotDependOnReportPackage() throws IOException {
        Path javaRoot = moduleRoot().resolve("src/main/java/net/lab1024/sa/admin/module/scm/finance");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(javaRoot)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    if (line.startsWith("import") && line.contains("module.scm.report")) {
                        violations.add(javaRoot.relativize(path) + " -> " + line.trim());
                    }
                }
            }
        }
        assertThat(violations)
                .as("report 域只读 finance 表，finance 域不得反向依赖 report")
                .isEmpty();
    }

    private void collectViolations(String sql, String where, List<String> sink) {
        Matcher matcher = WRITE_STATEMENT.matcher(sql);
        while (matcher.find()) {
            sink.add(where + " -> " + matcher.group(0).replaceAll("\\s+", " "));
        }
    }

    private List<String> stringFragmentsOf(String source) {
        List<String> fragments = new ArrayList<>();
        Matcher blocks = JAVA_TEXT_BLOCK.matcher(source);
        while (blocks.find()) {
            fragments.add(blocks.group(1));
        }
        String withoutTextBlocks = JAVA_TEXT_BLOCK.matcher(source).replaceAll("\"\"");
        Matcher literals = JAVA_STRING_LITERAL.matcher(withoutTextBlocks);
        while (literals.find()) {
            fragments.add(literals.group(1));
        }
        return fragments;
    }

    /**
     * 定位 {@code sa-admin} 模块目录。
     *
     * <p>Surefire 的 {@code user.dir} 就是模块目录，但 IDE 里单跑本用例时它可能是仓库根或
     * {@code xsy-scm-server}。逐级向上找，找不到就失败 —— 静默返回一个不存在的目录会让
     * 上面的 {@code assertThat(javaRoot).exists()} 之外的断言全部空转通过。
     */
    private Path moduleRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int depth = 0; depth < 4 && current != null; depth++) {
            if (Files.isDirectory(current.resolve("src/main/java/net/lab1024/sa/admin/module/scm/finance"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("找不到 sa-admin 模块目录（user.dir = " + System.getProperty("user.dir") + "）");
    }
}
