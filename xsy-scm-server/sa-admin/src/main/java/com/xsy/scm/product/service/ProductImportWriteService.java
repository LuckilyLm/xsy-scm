package com.xsy.scm.product.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.product.domain.form.ProductSpuAddForm;
import com.xsy.scm.product.domain.form.ProductSpuUpdateForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品导入的写入边界：整批在同一事务内逐个新增或更新，任一失败即整体回滚。解析和校验在无事务的 {@link ProductImportService} 中完成，确保先全量校验、0 错误才写。更新模式仍走
 * {@link ProductSpuService#update}， 乐观锁与分类、单位校验全部生效。
 */
@Service
@RequiredArgsConstructor
public class ProductImportWriteService {
    private final ProductSpuService productSpuService;

    @Transactional(rollbackFor = Exception.class)
    public List<Long> writeAll(List<ProductSpuAddForm> forms) {
        var ids = new ArrayList<Long>();
        for (int index = 0; index < forms.size(); index++) {
            try {
                ids.add(productSpuService.add(forms.get(index)));
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
                productSpuService.update(forms.get(index));
            } catch (RuntimeException exception) {
                throw new ImportProductException(index, exception);
            }
        }
        return forms.size();
    }

    /**
     * 携带失败商品序号，供无事务的导入解析服务把错误映射到对应 Excel 行。
     */
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
