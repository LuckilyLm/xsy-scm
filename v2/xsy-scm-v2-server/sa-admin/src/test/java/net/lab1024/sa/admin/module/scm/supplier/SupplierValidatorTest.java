package net.lab1024.sa.admin.module.scm.supplier;

import net.lab1024.sa.admin.module.scm.supplier.manager.SupplierValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 供应商字段归一化契约测试。 */
class SupplierValidatorTest {

    @Test
    @DisplayName("编码归一化：trim + upper")
    void normalizesCode() {
        assertThat(SupplierValidator.normalizeCode("  gys001 ")).isEqualTo("GYS001");
        assertThat(SupplierValidator.normalizeCode("Sup-01")).isEqualTo("SUP-01");
        assertThat(SupplierValidator.normalizeCode(null)).isNull();
    }

    @Test
    @DisplayName("名称归一化：仅 trim，保留大小写")
    void normalizesName() {
        assertThat(SupplierValidator.normalizeName("  鲜蔬源基地  ")).isEqualTo("鲜蔬源基地");
        assertThat(SupplierValidator.normalizeName("Acme Farms")).isEqualTo("Acme Farms");
        assertThat(SupplierValidator.normalizeName(null)).isNull();
    }

    @Test
    @DisplayName("联系人 / 地址归一化：空白视作未填写")
    void normalizesContactFields() {
        assertThat(SupplierValidator.normalizeOptional("  李四  ")).isEqualTo("李四");
        assertThat(SupplierValidator.normalizeOptional("   ")).isNull();
        assertThat(SupplierValidator.normalizeOptional("")).isNull();
        assertThat(SupplierValidator.normalizeOptional(null)).isNull();
    }

    @Test
    @DisplayName("备注可以被清空：空白 → null（配合 FieldStrategy.ALWAYS 真正写 NULL）")
    void remarkCanBeCleared() {
        assertThat(SupplierValidator.normalizeOptional("   ")).isNull();
        assertThat(SupplierValidator.normalizeOptional("长期合作")).isEqualTo("长期合作");
    }
}
