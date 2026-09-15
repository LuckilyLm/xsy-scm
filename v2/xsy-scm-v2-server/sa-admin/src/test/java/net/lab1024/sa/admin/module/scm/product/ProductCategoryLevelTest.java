package net.lab1024.sa.admin.module.scm.product;
import net.lab1024.sa.admin.module.scm.product.dao.*;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductCategoryEntity;
import net.lab1024.sa.admin.module.scm.product.service.ProductCategoryService;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProductCategoryLevelTest {
    private final ProductCategoryDao dao=mock(ProductCategoryDao.class);
    private final ProductCategoryService service=new ProductCategoryService(dao,mock(ProductSpuDao.class));
    @Test void derivesLevelFromEnabledParent() {
        var parent=new ProductCategoryEntity(); parent.setId(1L); parent.setLevel(2); parent.setStatus("ENABLED");
        when(dao.selectById(1L)).thenReturn(parent);
        assertThat(service.resolveLevel(1L,null)).isEqualTo(3);
        assertThat(service.resolveLevel(null,null)).isEqualTo(1);
    }
    @Test void rejectsFourthLevelDisabledSelfAndMissingParents() {
        var parent=new ProductCategoryEntity(); parent.setId(1L); parent.setLevel(3); parent.setStatus("ENABLED");
        when(dao.selectById(1L)).thenReturn(parent);
        rejects(1L,null,40010);
        parent.setLevel(1); parent.setStatus("DISABLED"); rejects(1L,null,40011);
        rejects(1L,1L,40011); rejects(2L,null,40410);
    }
    private void rejects(Long parent,Long self,int code) {
        assertThatThrownBy(() -> service.resolveLevel(parent,self)).isInstanceOfSatisfying(ScmBusinessException.class,
            e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
    }
}
