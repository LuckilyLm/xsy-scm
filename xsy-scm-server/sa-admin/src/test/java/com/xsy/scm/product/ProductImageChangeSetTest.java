package com.xsy.scm.product;

import com.xsy.scm.product.domain.entity.ProductImageEntity;
import com.xsy.scm.product.domain.form.ProductImageForm;
import com.xsy.scm.product.manager.ProductImageChangeSet;
import com.xsy.scm.common.exception.ScmBusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ProductImageChangeSetTest {
    @Test
    void primarySwitchPreservesIdentityAndSeparatesRemoval() {
        var a = new ProductImageEntity();
        a.setId(1L);
        var b = new ProductImageEntity();
        b.setId(2L);
        var keep = new ProductImageForm();
        keep.setImageId(2L);
        keep.setVersion(3);
        keep.setPrimaryFlag(true);
        var add = new ProductImageForm();
        var changes = ProductImageChangeSet.between(List.of(a, b), List.of(keep, add));
        assertThat(changes.removedIds()).containsExactly(1L);
        assertThat(changes.updated()).containsExactly(keep);
        assertThat(changes.inserted()).containsExactly(add);
    }

    @Test
    void foreignReferenceIsRejected() {
        var foreign = new ProductImageForm();
        foreign.setImageId(9L);
        foreign.setVersion(0);
        assertThatThrownBy(() -> ProductImageChangeSet.between(List.of(), List.of(foreign)))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(e.getErrorCode().getCode()).isEqualTo(40922));
    }
}
