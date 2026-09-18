package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;
import net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryMovementTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseInventoryContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W6 库存域常量与错误码（W6 Target Design §12.2）。
 *
 * <p>三件事必须被断言而不是靠人记得：
 * <ol>
 *   <li>{@code source_document_type} 的常量值与 W5 冻结的
 *       {@link PurchaseInventoryContract#SOURCE_DOCUMENT_TYPE} **逐字相等** ——
 *       库存域刻意不 import purchase 的常量（领域原语不该依赖某一个来源域），
 *       所以两个常量的一致性只能靠断言强制；</li>
 *   <li>枚举白名单只放行已支持的值（DB CHECK 与枚举必须同源）；</li>
 *   <li>错误码与**全部** SCM 域零交集 —— 这里用「全库码值集合无重复」的全局判据，
 *       而不是「W6 与 W5 比一次」，这样未来任何新域的撞码都会在这里先失败。</li>
 * </ol>
 */
@DisplayName("W6 库存域常量与错误码（单元）")
class ScmInventoryConstantTest {

    private static final String SCM_PACKAGE = "net.lab1024.sa.admin.module.scm";

    // ------------------------------------------------------------------
    // 常量一致性
    // ------------------------------------------------------------------

    @Test
    @DisplayName("source_document_type 常量与 W5 契约逐字一致（跨域常量靠断言强制）")
    void sourceDocumentTypeMatchesTheFrozenW5Contract() {
        assertThat(ScmInventorySourceDocumentTypeEnum.PURCHASE_RECEIPT_ITEM.name())
                .isEqualTo(PurchaseInventoryContract.SOURCE_DOCUMENT_TYPE)
                .isEqualTo("PURCHASE_RECEIPT_ITEM");
    }

    @Test
    @DisplayName("枚举白名单：只放行已支持的值，null / 空串 / 未支持值一律不放行")
    void enumsOnlyAllowSupportedValues() {
        assertThat(ScmInventoryMovementTypeEnum.isSupported("PURCHASE_IN")).isTrue();
        assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("PURCHASE_RECEIPT_ITEM")).isTrue();

        for (String rejected : new String[]{null, "", " ", "SALES_OUT", "TRANSFER_IN", "purchase_in"}) {
            assertThat(ScmInventoryMovementTypeEnum.isSupported(rejected))
                    .as("movement_type 不应放行 %s", rejected).isFalse();
            assertThat(ScmInventorySourceDocumentTypeEnum.isSupported(rejected))
                    .as("source_document_type 不应放行 %s", rejected).isFalse();
        }

        // W6-1 的排除清单：出库 / 调拨 / 盘点 / 报损报溢 / 规格转换 都不得提前出现在白名单里
        assertThat(ScmInventoryMovementTypeEnum.values()).hasSize(1);
        assertThat(ScmInventorySourceDocumentTypeEnum.values()).hasSize(1);
    }

    // ------------------------------------------------------------------
    // 错误码
    // ------------------------------------------------------------------

    @Test
    @DisplayName("错误码：恰好 4 个、码值冻结、段内无重复")
    void errorCodesAreFrozenAndUniqueWithinTheDomain() {
        assertThat(InventoryErrorCode.values()).hasSize(4);

        assertThat(InventoryErrorCode.INVENTORY_BALANCE_NOT_FOUND.getCode()).isEqualTo(40486);
        assertThat(InventoryErrorCode.INVENTORY_UNIT_MISMATCH.getCode()).isEqualTo(41001);
        assertThat(InventoryErrorCode.INVENTORY_DUPLICATE_INBOUND.getCode()).isEqualTo(41002);
        assertThat(InventoryErrorCode.INVENTORY_PARAM_INVALID.getCode()).isEqualTo(41003);

        Set<Integer> codes = Arrays.stream(InventoryErrorCode.values())
                .map(InventoryErrorCode::getCode).collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(codes).as("库存域段内出现重复码值").hasSize(4);

        // 段归属：404xx 一个（NOT_FOUND）+ 410xx 三个（业务冲突 / 参数非法）
        assertThat(codes.stream().filter(code -> code / 100 == 404).count()).isEqualTo(1L);
        assertThat(codes.stream().filter(code -> code / 100 == 410).count()).isEqualTo(3L);
        // 消息不得为空 —— 错误码没有可读消息等于没有错误码
        Arrays.stream(InventoryErrorCode.values())
                .forEach(code -> assertThat(code.getMsg()).isNotBlank());
    }

    @Test
    @DisplayName("撞码门禁：W6 的 4 个码与其它全部 SCM 域零交集")
    void inventoryErrorCodesDoNotCollideWithAnyOtherDomain() {
        List<Class<?>> domains = discoverErrorCodeEnums();
        Set<String> names = domains.stream()
                .map(Class::getSimpleName).collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(names)
                .as("扫描/回退清单未覆盖全部 SCM 错误码枚举，门禁会漏放")
                .containsAll(KNOWN_ENUMS);
        assertThat(domains.size())
                .as("扫描到的错误码枚举少于已知的 %d 个：扫描静默失效时请把新域补进 FALLBACK",
                        KNOWN_ENUMS.size())
                .isGreaterThanOrEqualTo(KNOWN_ENUMS.size());

        Set<Integer> others = new LinkedHashSet<>();
        for (Class<?> domain : domains) {
            if (domain == InventoryErrorCode.class) {
                continue;
            }
            for (Object constant : domain.getEnumConstants()) {
                others.add(((ScmErrorCode) constant).getCode());
            }
        }
        assertThat(others).as("其它 SCM 域的错误码集合不应为空").isNotEmpty();

        for (InventoryErrorCode code : InventoryErrorCode.values()) {
            assertThat(others)
                    .as("W6 错误码 %s(%d) 与既有 SCM 域已占用的码冲突", code.name(), code.getCode())
                    .doesNotContain(code.getCode());
        }
    }

    @Test
    @DisplayName("既有观察：40921 VERSION_CONFLICT 在多个域里被重复声明（W6 不复制这种写法）")
    void versionConflictIsDeliberatelyNotReplicatedByW6() {
        // 扫描结果暴露了一个既有事实：ProductErrorCode / PurchaseErrorCode / ScmCommonErrorCode
        // 都各自声明了 40921「数据已被其他操作修改」。按 AGENTS 的稳定码纪律，
        // 「可复用的既有码不复制语义」—— 库存域因此直接复用 ScmCommonErrorCode.VERSION_CONFLICT
        // （见 InventoryCommandService），不新增自己的 40921。
        //
        // 本用例把这个事实钉住：一旦有人给库存域也加一个 40921，上面的撞码门禁会失败，
        // 而这里说明**为什么** W6 选择复用而不是复制。清理既有三处重复属于 W1/W2/W5 的
        // 独立重构，不在 W6-1 范围内。
        assertThat(InventoryErrorCode.values())
                .extracting(InventoryErrorCode::getCode)
                .doesNotContain(40921);
    }

    // ------------------------------------------------------------------
    // classpath 扫描（与 W5 的撞码门禁同一手法：反查目录，未来新域自动纳入）
    // ------------------------------------------------------------------

    /**
     * 回退清单：classpath 不是展开目录时使用（例如从 jar 运行）。
     *
     * <p><b>新增 SCM 域时必须在这里补一行</b> —— 上面的
     * {@code containsAll(KNOWN_ENUMS)} 断言就是为此存在的：漏补会让门禁少比一个域，
     * 而那种漏放不会以任何形式报警。
     */
    private static final List<String> FALLBACK = List.of(
            "net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode",
            "net.lab1024.sa.admin.module.scm.product.constant.ProductErrorCode",
            "net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode",
            "net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode",
            "net.lab1024.sa.admin.module.scm.pricing.constant.PricingErrorCode",
            "net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode",
            "net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode",
            "net.lab1024.sa.admin.module.scm.warehouse.constant.WarehouseErrorCode",
            InventoryErrorCode.class.getName());

    /** 回退清单对应的简单名（用于断言覆盖完整）。 */
    private static final Set<String> KNOWN_ENUMS = Set.of(
            "ScmCommonErrorCode", "ProductErrorCode", "CustomerErrorCode", "SupplierErrorCode",
            "PricingErrorCode", "OrderErrorCode", "PurchaseErrorCode", "WarehouseErrorCode",
            InventoryErrorCode.class.getSimpleName());

    private static List<Class<?>> discoverErrorCodeEnums() {
        List<Class<?>> found = new ArrayList<>();
        try {
            URL root = ScmErrorCode.class.getResource("/net/lab1024/sa/admin/module/scm");
            if (root != null && "file".equals(root.getProtocol())) {
                Path base = Paths.get(root.toURI());
                try (Stream<Path> walk = Files.walk(base)) {
                    for (Path path : walk.filter(Files::isRegularFile).toList()) {
                        String fileName = path.getFileName().toString();
                        if (!fileName.endsWith("ErrorCode.class") || fileName.contains("$")) {
                            continue;
                        }
                        String relative = base.relativize(path).toString()
                                .replace(File.separatorChar, '.').replace('/', '.');
                        Class<?> type = load(SCM_PACKAGE + "."
                                + relative.substring(0, relative.length() - ".class".length()));
                        if (type != null && type.isEnum() && ScmErrorCode.class.isAssignableFrom(type)) {
                            found.add(type);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 目录不可读 → 走回退清单
        }
        if (!found.isEmpty()) {
            return found;
        }
        return FALLBACK.stream().map(ScmInventoryConstantTest::load)
                .filter(java.util.Objects::nonNull).collect(Collectors.toList());
    }

    private static Class<?> load(String fqn) {
        try {
            return Class.forName(fqn, false, ScmErrorCode.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
