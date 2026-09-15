package net.lab1024.sa.admin.module.scm.supplier;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierSkuEntity;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierSkuItemForm;
import net.lab1024.sa.admin.module.scm.supplier.manager.SupplierSkuChangeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 整表替换差量计算契约测试。
 *
 * <p>其中 {@link #keepsMultipleDefaultsWithoutInventingCardinalityPolicy()} 是 legacy 不变量 R12 的
 * <b>防回归门禁</b>：legacy 明确允许同一供应商存在多条默认来源，并禁止发明基数策略。
 * 如果将来有人「顺手」加上「每供应商只能一条默认」的校验，这条用例必须先失败。
 */
class SupplierSkuChangeSetTest {

    private static int codeOf(Throwable t) {
        return ((ScmBusinessException) t).getErrorCode().getCode();
    }

    private static SupplierSkuEntity row(Long id, Long skuId, Integer version) {
        SupplierSkuEntity entity = new SupplierSkuEntity();
        entity.setId(id);
        entity.setSkuId(skuId);
        entity.setVersion(version);
        return entity;
    }

    private static SupplierSkuItemForm item(Long id, Integer version, Long skuId) {
        SupplierSkuItemForm form = new SupplierSkuItemForm();
        form.setId(id);
        form.setVersion(version);
        form.setSkuId(skuId);
        form.setPurchaseUnit("箱");
        return form;
    }

    @Test
    @DisplayName("保留 / 新增 / 删除三类动作分离")
    void separatesRetainedInsertedRemoved() {
        var keep = row(7L, 70L, 2);
        var removed = row(8L, 80L, 1);

        var keepForm = item(7L, 2, 70L);
        var addForm = item(null, null, 90L);

        var changeSet = SupplierSkuChangeSet.between(List.of(keep, removed), List.of(keepForm, addForm));

        assertThat(changeSet.retained()).hasSize(1);
        assertThat(changeSet.retained().get(0).existing().getId()).isEqualTo(7L);
        assertThat(changeSet.retained().get(0).requested()).isSameAs(keepForm);
        assertThat(changeSet.inserted()).containsExactly(addForm);
        assertThat(changeSet.removedIds()).containsExactly(8L);
    }

    @Test
    @DisplayName("带 id 但不在该供应商名下 → 40943")
    void rejectsForeignIdentity() {
        var existing = List.of(row(7L, 70L, 0));
        var foreign = item(99L, 0, 99L);

        assertThatThrownBy(() -> SupplierSkuChangeSet.between(existing, List.of(foreign)))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40943));
    }

    @Test
    @DisplayName("既有行的 skuId 被变更 → 40943（应删旧增新，而不是改行）")
    void rejectsSkuIdMutation() {
        var existing = List.of(row(7L, 70L, 0));
        var mutated = item(7L, 0, 71L);

        assertThatThrownBy(() -> SupplierSkuChangeSet.between(existing, List.of(mutated)))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40943));
    }

    @Test
    @DisplayName("请求内 skuId 重复 → 40943")
    void rejectsRepeatedSkuId() {
        var first = item(null, null, 90L);
        var second = item(null, null, 90L);

        assertThatThrownBy(() -> SupplierSkuChangeSet.between(List.of(), List.of(first, second)))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40943));
    }

    @Test
    @DisplayName("带 id 的行版本不一致 → 40921")
    void rejectsStaleVersion() {
        var existing = List.of(row(7L, 70L, 5));
        var stale = item(7L, 4, 70L);

        assertThatThrownBy(() -> SupplierSkuChangeSet.between(existing, List.of(stale)))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40921));
    }

    @Test
    @DisplayName("无 id 但 (supplierId, skuId) 已存在 → 复用既有行，不新增")
    void reusesExistingRowWhenSkuMatches() {
        var existing = List.of(row(7L, 70L, 3));
        var withoutId = item(null, null, 70L);

        var changeSet = SupplierSkuChangeSet.between(existing, List.of(withoutId));

        assertThat(changeSet.inserted()).isEmpty();
        assertThat(changeSet.removedIds()).isEmpty();
        assertThat(changeSet.retained()).hasSize(1);
        assertThat(changeSet.retained().get(0).existing().getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("无 id 复用但显式带了过期 version → 40921")
    void rejectsStaleVersionOnReuse() {
        var existing = List.of(row(7L, 70L, 3));
        var withoutId = item(null, 1, 70L);

        assertThatThrownBy(() -> SupplierSkuChangeSet.between(existing, List.of(withoutId)))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40921));
    }

    @Test
    @DisplayName("空数组 = 清空全部关联")
    void emptyRequestRemovesEverything() {
        var existing = List.of(row(1L, 10L, 0), row(2L, 20L, 0), row(3L, 30L, 0));

        var changeSet = SupplierSkuChangeSet.between(existing, List.of());

        assertThat(changeSet.retained()).isEmpty();
        assertThat(changeSet.inserted()).isEmpty();
        assertThat(changeSet.removedIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("null 请求列表按空处理（清空全部）")
    void nullRequestBehavesAsEmpty() {
        var changeSet = SupplierSkuChangeSet.between(List.of(row(1L, 10L, 0)), null);

        assertThat(changeSet.removedIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("R12 防回归：同一供应商多条 defaultFlag=true 全部保留，不发明基数策略")
    void keepsMultipleDefaultsWithoutInventingCardinalityPolicy() {
        var first = row(1L, 10L, 0);
        var second = row(2L, 20L, 0);

        var firstForm = item(1L, 0, 10L);
        firstForm.setDefaultFlag(true);
        var secondForm = item(2L, 0, 20L);
        secondForm.setDefaultFlag(true);

        var changeSet = SupplierSkuChangeSet.between(List.of(first, second), List.of(firstForm, secondForm));

        assertThat(changeSet.retained()).hasSize(2);
        assertThat(changeSet.retained()).allSatisfy(m -> assertThat(m.requested().getDefaultFlag()).isTrue());
        assertThat(changeSet.inserted()).isEmpty();
        assertThat(changeSet.removedIds()).isEmpty();
    }

    @Test
    @DisplayName("空库 + 全新增：全部进 inserted")
    void allInsertWhenNothingExists() {
        var changeSet = SupplierSkuChangeSet.between(List.of(), List.of(item(null, null, 1L), item(null, null, 2L)));

        assertThat(changeSet.inserted()).hasSize(2);
        assertThat(changeSet.retained()).isEmpty();
        assertThat(changeSet.removedIds()).isEmpty();
    }
}
