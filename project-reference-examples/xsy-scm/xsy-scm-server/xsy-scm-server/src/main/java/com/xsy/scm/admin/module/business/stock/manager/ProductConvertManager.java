package com.xsy.scm.admin.module.business.stock.manager;

import com.xsy.scm.admin.module.business.stock.dao.ProductConvertDao;
import com.xsy.scm.admin.module.business.stock.dao.ProductConvertItemDao;
import com.xsy.scm.admin.module.business.stock.domain.entity.ProductConvertEntity;
import com.xsy.scm.admin.module.business.stock.domain.entity.ProductConvertItemEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商品转换单 Manager
 *
 * <p>聚合转换单 / 明细两表的原子操作。</p>
 *
 * @author xsy-scm
 */
@Service
public class ProductConvertManager {

    @Resource
    private ProductConvertDao productConvertDao;

    @Resource
    private ProductConvertItemDao productConvertItemDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(ProductConvertEntity entity) {
        productConvertDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(ProductConvertEntity entity) {
        productConvertDao.updateById(entity);
    }

    /**
     * 覆盖式保存明细：先逻辑删除原明细，再插入新明细
     */
    @Transactional(rollbackFor = Throwable.class)
    public void replaceItems(Long convertId, List<ProductConvertItemEntity> items) {
        productConvertItemDao.batchUpdateDeletedByConvertId(convertId);
        for (ProductConvertItemEntity item : items) {
            item.setDeletedFlag(Boolean.FALSE);
            productConvertItemDao.insert(item);
        }
    }
}
