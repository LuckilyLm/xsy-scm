package com.xsy.scm.admin.module.business.stock.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.stock.dao.StockCheckItemDao;
import com.xsy.scm.admin.module.business.stock.domain.entity.StockCheckItemEntity;
import com.xsy.scm.admin.module.business.stock.domain.form.StockCheckItemAddForm;
import com.xsy.scm.admin.module.business.stock.domain.form.StockCheckItemQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.form.StockCheckItemUpdateForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.StockCheckItemVO;
import com.xsy.scm.admin.module.business.stock.manager.StockCheckItemManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 库存盘点明细 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class StockCheckItemService {

    @Resource
    private StockCheckItemDao checkItemDao;

    @Resource
    private StockCheckItemManager checkItemManager;

    /**
     * 分页查询盘点明细（按盘点单维度）
     */
    public ResponseDTO<PageResult<StockCheckItemVO>> query(StockCheckItemQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<StockCheckItemVO> list = checkItemDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增盘点明细，差异数量自动计算（实盘 - 账面）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(StockCheckItemAddForm addForm) {
        StockCheckItemEntity entity = SmartBeanUtil.copy(addForm, StockCheckItemEntity.class);
        entity.setDeletedFlag(Boolean.FALSE);
        entity.setDiffQuantity(computeDiff(addForm.getActualQuantity(), addForm.getBookQuantity()));
        checkItemManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新盘点明细，差异数量自动计算（实盘 - 账面）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(StockCheckItemUpdateForm updateForm) {
        StockCheckItemEntity entity = SmartBeanUtil.copy(updateForm, StockCheckItemEntity.class);
        entity.setDiffQuantity(computeDiff(updateForm.getActualQuantity(), updateForm.getBookQuantity()));
        checkItemManager.update(entity);
        return ResponseDTO.ok();
    }

    private BigDecimal computeDiff(BigDecimal actual, BigDecimal book) {
        if (actual == null || book == null) {
            return null;
        }
        return actual.subtract(book);
    }

    /**
     * 删除盘点明细（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long itemId) {
        checkItemDao.batchUpdateDeleted(Collections.singletonList(itemId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除盘点明细（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        checkItemDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }
}
