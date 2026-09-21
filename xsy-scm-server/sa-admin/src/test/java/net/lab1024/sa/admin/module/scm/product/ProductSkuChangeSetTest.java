package net.lab1024.sa.admin.module.scm.product;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductSkuEntity;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSkuForm;
import net.lab1024.sa.admin.module.scm.product.manager.ProductSkuChangeSet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ProductSkuChangeSetTest {
    @Test
    void preservesExistingIdentityAndSeparatesInsertUpdateAndRemove() {
        var old = new ProductSkuEntity();
        old.setId(7L);
        old.setVersion(2);
        var removed = new ProductSkuEntity();
        removed.setId(8L);
        var keep = new ProductSkuForm();
        keep.setSkuId(7L);
        keep.setVersion(2);
        var add = new ProductSkuForm();
        var changes = ProductSkuChangeSet.between(List.of(old, removed), List.of(keep, add));
        assertThat(changes.updated()).containsExactly(keep);
        assertThat(changes.inserted()).containsExactly(add);
        assertThat(changes.removedIds()).containsExactly(8L);
        assertThat(keep.getSkuId()).isEqualTo(7L);
        assertThat(keep.getVersion()).isEqualTo(2);
    }

    @Test
    void rejectsForeignAndRepeatedIdentity() {
        var old = new ProductSkuEntity();
        old.setId(7L);
        var foreign = new ProductSkuForm();
        foreign.setSkuId(8L);
        foreign.setVersion(0);
        assertThatThrownBy(() -> ProductSkuChangeSet.between(List.of(old), List.of(foreign)))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(e.getErrorCode().getCode()).isEqualTo(40920));
        foreign.setSkuId(7L);
        assertThatThrownBy(() -> ProductSkuChangeSet.between(List.of(old), List.of(foreign, foreign))).isInstanceOf(ScmBusinessException.class);
    }
}
