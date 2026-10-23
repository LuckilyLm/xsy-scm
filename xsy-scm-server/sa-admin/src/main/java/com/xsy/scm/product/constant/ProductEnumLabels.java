package com.xsy.scm.product.constant;

import com.xsy.scm.common.constant.ScmProductTypeEnum;
import com.xsy.scm.common.constant.ScmShelfStatusEnum;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 商品导入导出共用的枚举中文标签。
 *
 * <p>
 * 一份定义同时供三处使用：模板下拉选项、导入解析归一化、导出单元格取值。 分开维护迟早漂移成「导出写中文、导入只认英文」这种自相矛盾。
 *
 * <p>
 * 中文标签一律取自枚举的 {@code desc}，本类只声明「哪一列对应哪个枚举」。 落库与接口仍用枚举名。
 */
public final class ProductEnumLabels {

    /**
     * 列名 → 下拉选项。顺序即模板下拉里的展示顺序。
     */
    public static final Map<String,
        List<String>> OPTIONS = Map.of("储存方式", labels(ScmStorageMethodEnum.values(), ScmStorageMethodEnum::getDesc),
            "主档状态", labels(ScmProductMasterStatusEnum.values(), ScmProductMasterStatusEnum::getDesc), "商品状态",
            labels(ScmShelfStatusEnum.values(), ScmShelfStatusEnum::getDesc), "商品规格状态",
            labels(ScmShelfStatusEnum.values(), ScmShelfStatusEnum::getDesc), "商品类型",
            labels(ScmProductTypeEnum.values(), ScmProductTypeEnum::getDesc));

    private static final Map<String,
        Map<String, String>> ALIASES = Map.of("储存方式",
            aliases(ScmStorageMethodEnum.values(), ScmStorageMethodEnum::getDesc), "主档状态",
            aliases(ScmProductMasterStatusEnum.values(), ScmProductMasterStatusEnum::getDesc), "商品状态",
            aliases(ScmShelfStatusEnum.values(), ScmShelfStatusEnum::getDesc), "商品规格状态",
            aliases(ScmShelfStatusEnum.values(), ScmShelfStatusEnum::getDesc), "商品类型",
            aliases(ScmProductTypeEnum.values(), ScmProductTypeEnum::getDesc));

    private ProductEnumLabels() {
    }

    /**
     * 枚举的 {@code desc} 即模板下拉的展示值，顺序沿用枚举声明顺序。
     */
    private static <E extends Enum<E>> List<String> labels(E[] values, Function<E, String> desc) {
        return Arrays.stream(values).map(desc).toList();
    }

    /**
     * 中文标签 → 枚举名，供导入解析把「常温」还原成 {@code AMBIENT}。
     */
    private static <E extends Enum<E>> Map<String, String> aliases(E[] values, Function<E, String> desc) {
        Map<String, String> aliases = new LinkedHashMap<>(values.length);
        for (E value : values) {
            aliases.put(desc.apply(value), value.name());
        }
        return Map.copyOf(aliases);
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
