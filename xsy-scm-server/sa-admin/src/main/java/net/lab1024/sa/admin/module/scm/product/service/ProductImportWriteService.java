package net.lab1024.sa.admin.module.scm.product.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSpuAddForm;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSpuUpdateForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品导入的写入边界：整批在同一个事务内逐个新增或逐个更新，任一失败即整体回滚。
 * 与解析/校验分离（{@link ProductImportService} 无事务），确保「先全量校验、0 错误才写」。
 * 更新模式不另建旁路：仍走 {@link ProductSpuService#update}，因此乐观锁、分类与单位校验全部生效。
 */
@Service
@RequiredArgsConstructor
public class ProductImportWriteService {
    private final ProductSpuService service;

    @Transactional(rollbackFor = Exception.class)
    public List<Long> writeAll(List<ProductSpuAddForm> forms) {
        var ids = new ArrayList<Long>();
        for (int index = 0; index < forms.size(); index++) {
            try {
                ids.add(service.add(forms.get(index)));
            } catch (RuntimeException exception) {
                // 让异常穿过事务代理，整批回滚；调用方按 productIndex 回填定位错误。
                throw new ImportProductException(index, exception);
            }
        }
        return ids;
    }

    @Transactional(rollbackFor = Exception.class)
    public int writeUpdates(List<ProductSpuUpdateForm> forms) {
        for (int index = 0; index < forms.size(); index++) {
            try {
                service.update(forms.get(index));
            } catch (RuntimeException exception) {
                throw new ImportProductException(index, exception);
            }
        }
        return forms.size();
    }

    /** 携带失败商品序号，供无事务的导入解析服务把错误映射到对应 Excel 行。 */
    public static final class ImportProductException extends RuntimeException {
        private final int productIndex;

        public ImportProductException(int productIndex, RuntimeException cause) {
            super(cause);
            this.productIndex = productIndex;
        }

        public int getProductIndex() {
            return productIndex;
        }
    }
}
