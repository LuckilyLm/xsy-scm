package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;
import net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode;
import net.lab1024.sa.admin.module.scm.warehouse.constant.WarehouseErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W5 错误码撞码门禁（W5 Target Design §7.7 Q11 / §11.1）。
 *
 * <p>锁定三件事：
 * <ol>
 *   <li>{@link PurchaseErrorCode} 恰好 **39** 个、{@link WarehouseErrorCode} 恰好 **8** 个，合计 **47**
 *       （W5 冻结 40 个，B1 加 1、出库波次加 1、调拨波次加 1，另有 W5 内的 39 与 2 的口径见下方断言）；</li>
 *   <li>W5+B1 的 47 个码**段内无重复**，且段分布为 400xx=12 · 404xx=6 · 409xx=22 · 410xx=7；</li>
 *   <li>W5 的 47 个码与 **W1–W4 全部** SCM 错误码**零交集**，且两个枚举之间也零重复。</li>
 * </ol>
 *
 * <p>**为什么自动扫描而不是硬编码清单**：硬编码清单在 W6+ 新增域时会被忘记更新，
 * 门禁就形同虚设。这里从 classpath 上 {@code module/scm} 目录反查所有
 * {@code *ErrorCode} 枚举（实现 {@link ScmErrorCode} 且为 enum），
 * 因此**未来任何新域的错误码都会被自动纳入比对**。
 * 目录不可读时（例如从 jar 运行）回退到显式清单，并断言清单里 6 个 W1–W4 枚举确实被扫到。
 */
class PurchaseErrorCodeTest {

    private static final String SCM_PACKAGE = "net.lab1024.sa.admin.module.scm";

    /** 回退清单：classpath 不是展开目录时使用。新域的错误码枚举要在这里补一行。 */
    private static final List<String> FALLBACK = List.of(
            "net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode",
            "net.lab1024.sa.admin.module.scm.product.constant.ProductErrorCode",
            "net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode",
            "net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode",
            "net.lab1024.sa.admin.module.scm.pricing.constant.PricingErrorCode",
            "net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode",
            PurchaseErrorCode.class.getName(),
            WarehouseErrorCode.class.getName(),
            // W6 库存域（V19/V20）：本类只断言「W5 的 40 个码不撞车」，
            // W6 与其余域的撞码由 ScmInventoryConstantTest 的全库唯一性判据负责。
            "net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode");

    /** W1–W4 已存在的错误码枚举，必须被扫描到（防止扫描静默失效）。 */
    private static final Set<String> W1_TO_W4_ENUMS = Set.of(
            "ScmCommonErrorCode", "ProductErrorCode", "CustomerErrorCode",
            "SupplierErrorCode", "PricingErrorCode", "OrderErrorCode");

    @Test
    @DisplayName("撞码门禁：W5+B1 合计 46 码、段内无重复、段分布正确、与 W1–W4 零交集")
    void gate() {
        // ---------- 1. 数量 ----------
        assertThat(PurchaseErrorCode.values()).hasSize(39);
        // 出库波次新增 WAREHOUSE_DEFAULT_AMBIGUOUS(41018)，仓库域由 6 增至 7；
        // 调拨波次新增 WAREHOUSE_DISABLE_HAS_IN_TRANSIT_TRANSFER(41009)，再增至 8。
        assertThat(WarehouseErrorCode.values()).hasSize(8);

        Map<String, Integer> w5 = new LinkedHashMap<>();
        Arrays.stream(PurchaseErrorCode.values())
                .forEach(c -> w5.put("PurchaseErrorCode." + c.name(), c.getCode()));
        Arrays.stream(WarehouseErrorCode.values())
                .forEach(c -> w5.put("WarehouseErrorCode." + c.name(), c.getCode()));
        assertThat(w5).as("W5+B1 错误码合计").hasSize(47);

        // ---------- 2. 段内无重复 ----------
        Set<Integer> w5Codes = new LinkedHashSet<>(w5.values());
        assertThat(w5Codes).as("W5+B1 段内存在重复码值").hasSize(47);

        // ---------- 3. 段分布 ----------
        Set<Integer> purchaseCodes = Arrays.stream(PurchaseErrorCode.values())
                .map(PurchaseErrorCode::getCode).collect(Collectors.toSet());
        assertThat(purchaseCodes.stream().filter(c -> c / 100 == 400).count())
                .as("PurchaseErrorCode 400xx 段").isEqualTo(12L);
        assertThat(purchaseCodes.stream().filter(c -> c / 100 == 404).count())
                .as("PurchaseErrorCode 404xx 段").isEqualTo(5L);
        assertThat(purchaseCodes.stream().filter(c -> c / 100 == 409).count())
                .as("PurchaseErrorCode 409xx 段").isEqualTo(21L);
        assertThat(purchaseCodes.stream().filter(c -> c / 100 == 410).count())
                .as("PurchaseErrorCode 410xx 段").isEqualTo(1L);
        assertThat(purchaseCodes).hasSize(39);

        assertThat(WarehouseErrorCode.WAREHOUSE_NOT_FOUND.getCode()).isEqualTo(40485);
        assertThat(WarehouseErrorCode.WAREHOUSE_CODE_DUPLICATE.getCode()).isEqualTo(40996);
        assertThat(WarehouseErrorCode.WAREHOUSE_STATE_INVALID.getCode()).isEqualTo(41004);
        assertThat(WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_BALANCE.getCode()).isEqualTo(41005);
        assertThat(WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_INBOUND.getCode()).isEqualTo(41006);
        assertThat(WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_PENDING_PUTAWAY.getCode()).isEqualTo(41007);
        // 出库波次：启用仓库不唯一时无法解析「默认仓库」（订单无仓库字段，G-03）
        assertThat(WarehouseErrorCode.WAREHOUSE_DEFAULT_AMBIGUOUS.getCode()).isEqualTo(41018);
        // 调拨波次：在途调拨单阻塞仓库停用（第四条停用阻塞条件）
        assertThat(WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_IN_TRANSIT_TRANSFER.getCode()).isEqualTo(41009);

        // 合并后的段分布：400xx=12 · 404xx=6（含 40485）· 409xx=22（含 40996）· 410xx=7
        assertThat(w5Codes.stream().filter(c -> c / 100 == 400).count()).isEqualTo(12L);
        assertThat(w5Codes.stream().filter(c -> c / 100 == 404).count()).isEqualTo(6L);
        assertThat(w5Codes.stream().filter(c -> c / 100 == 409).count()).isEqualTo(22L);
        assertThat(w5Codes.stream().filter(c -> c / 100 == 410).count()).isEqualTo(7L);

        // ---------- 4. 与 W1–W4 零交集 ----------
        List<Class<?>> discovered = discover();
        Set<String> discoveredNames = discovered.stream()
                .map(Class::getSimpleName).collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(discoveredNames)
                .as("扫描未覆盖 W1–W4 全部错误码枚举，门禁会漏放")
                .containsAll(W1_TO_W4_ENUMS);
        assertThat(discoveredNames)
                .as("扫描未覆盖 W5 自己的两个枚举")
                .contains(PurchaseErrorCode.class.getSimpleName(), WarehouseErrorCode.class.getSimpleName());

        Set<Integer> otherCodes = new LinkedHashSet<>();
        for (Class<?> type : discovered) {
            if (type == PurchaseErrorCode.class || type == WarehouseErrorCode.class) {
                continue;
            }
            for (Object constant : type.getEnumConstants()) {
                otherCodes.add(((ScmErrorCode) constant).getCode());
            }
        }
        assertThat(otherCodes).as("W1–W4 错误码集合不应为空").isNotEmpty();

        for (Map.Entry<String, Integer> entry : w5.entrySet()) {
            assertThat(otherCodes)
                    .as("W5 错误码 %s(%d) 与 W1–W4 已占用码冲突", entry.getKey(), entry.getValue())
                    .doesNotContain(entry.getValue());
        }
    }

    // ------------------------------------------------------------------
    // classpath 扫描
    // ------------------------------------------------------------------

    private static List<Class<?>> discover() {
        try {
            URL root = ScmErrorCode.class.getResource("/net/lab1024/sa/admin/module/scm");
            if (root != null && "file".equals(root.getProtocol())) {
                Path base = Paths.get(root.toURI());
                List<Class<?>> found = new ArrayList<>();
                try (Stream<Path> walk = Files.walk(base)) {
                    for (Path path : walk.filter(Files::isRegularFile).collect(Collectors.toList())) {
                        String fileName = path.getFileName().toString();
                        if (!fileName.endsWith("ErrorCode.class") || fileName.contains("$")) {
                            continue;
                        }
                        String relative = base.relativize(path).toString()
                                .replace(File.separatorChar, '.').replace('/', '.');
                        String fqn = SCM_PACKAGE + "." + relative.substring(0, relative.length() - ".class".length());
                        Class<?> type = load(fqn);
                        if (type != null && type.isEnum() && ScmErrorCode.class.isAssignableFrom(type)) {
                            found.add(type);
                        }
                    }
                }
                if (!found.isEmpty()) {
                    return found;
                }
            }
        } catch (Exception ignored) {
            // 落到回退清单
        }
        return FALLBACK.stream().map(PurchaseErrorCodeTest::load)
                .filter(Objects::nonNull).collect(Collectors.toList());
    }

    private static Class<?> load(String fqn) {
        try {
            return Class.forName(fqn, false, ScmErrorCode.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
