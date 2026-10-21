package com.xsy.scm.product.constant;

import com.xsy.scm.common.constant.ScmProductTypeEnum;
import com.xsy.scm.common.constant.ScmShelfStatusEnum;

import java.util.List;
import java.util.Map;

/**
 * 商品导入导出共用的枚举中文标签。
 *
 * <p>
 * 一份定义同时供三处使用：模板下拉选项、导入解析归一化、导出单元格取值。 分开维护迟早漂移成「导出写中文、导入只认英文」这种自相矛盾。
 *
 * <p>
 * 中文标签是给现场看的业务用语；落库与接口仍用枚举名，两边的对应关系只在这里维护。
 */
public final class ProductEnumLabels {

    /** 列名 → 下拉选项。顺序即模板下拉里的展示顺序。 */
    public static final Map<String,
            List<String>> OPTIONS = Map.of("储存方式", List.of("常温", "冷藏", "冷冻"), "主档状态", List.of("启用", "停用", "归档"), "商品状态",
                    List.of("上架", "下架"), "商品规格状态", List.of("上架", "下架"), "商品类型", List.of("标品", "非标品"));

    private static final Map<String, Map<String, String>> ALIASES = Map.of("储存方式",
            Map.of("常温", ScmStorageMethodEnum.AMBIENT.name(), "冷藏", ScmStorageMethodEnum.CHILLED.name(), "冷冻",
                    ScmStorageMethodEnum.FROZEN.name()),
            "主档状态",
            Map.of("启用", ScmProductMasterStatusEnum.ENABLED.name(), "停用", ScmProductMasterStatusEnum.DISABLED.name(),
                    "归档", ScmProductMasterStatusEnum.ARCHIVED.name()),
            "商品状态", Map.of("上架", ScmShelfStatusEnum.ON_SHELF.name(), "下架", ScmShelfStatusEnum.OFF_SHELF.name()),
            "商品规格状态", Map.of("上架", ScmShelfStatusEnum.ON_SHELF.name(), "下架", ScmShelfStatusEnum.OFF_SHELF.name()),
            "商品类型", Map.of("标品", ScmProductTypeEnum.STANDARD.name(), "非标品", ScmProductTypeEnum.NON_STANDARD.name()));

    private ProductEnumLabels() {
    }

    /**
     * 中文标签转枚举名。不在映射表里的取值原样返回，交给服务端枚举校验拒绝并给出可读提示。
     */
    public static String normalize(String column, String value) {
        if (value == null) {
            return null;
        }
        var aliases = ALIASES.get(column);
        return aliases == null ? value : aliases.getOrDefault(value, value);
    }

    /**
     * 枚举名转中文标签。映射不到的取值原样返回，避免导出的单元格变成空白。
     */
    public static String label(String column, String enumName) {
        if (enumName == null) {
            return null;
        }
        var aliases = ALIASES.get(column);
        if (aliases == null) {
            return enumName;
        }
        for (var entry : aliases.entrySet()) {
            if (entry.getValue().equals(enumName)) {
                return entry.getKey();
            }
        }
        return enumName;
    }
}
