package com.xsy.scm.admin.module.business.stock.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.stock.constant.AdjustStatusEnum;
import com.xsy.scm.admin.module.business.stock.dao.StockAdjustDao;
import com.xsy.scm.admin.module.business.stock.domain.entity.StockAdjustEntity;
import com.xsy.scm.admin.module.business.stock.domain.form.StockAdjustAddForm;
import com.xsy.scm.admin.module.business.stock.domain.form.StockAdjustQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.form.StockAdjustUpdateForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.StockAdjustVO;
import com.xsy.scm.admin.module.business.stock.manager.StockAdjustManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import com.xsy.scm.admin.module.business.stock.constant.AdjustTypeEnum;
import com.xsy.scm.admin.module.business.stock.domain.form.StockAdjustApproveForm;
import com.xsy.scm.admin.module.business.stock.domain.form.StockOperateForm;
import com.xsy.scm.base.module.support.serialnumber.constant.SerialNumberIdEnum;
import com.xsy.scm.base.module.support.serialnumber.service.SerialNumberService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 库存调整单 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class StockAdjustService {

    @Resource
    private StockAdjustDao stockAdjustDao;

    @Resource
    private StockAdjustManager stockAdjustManager;

    @Resource
    private SerialNumberService serialNumberService;

    @Resource
    private StockOperateService stockOperateService;

    /**
     * 分页查询库存调整单
     */
    public ResponseDTO<PageResult<StockAdjustVO>> query(StockAdjustQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<StockAdjustVO> list = stockAdjustDao.queryPage(page, queryForm);
        PageResult<StockAdjustVO> pageResult = SmartPageUtil.convert2PageResult(page, list);
        return ResponseDTO.ok(pageResult);
    }

    /**
     * 新增库存调整单（待审核态）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(StockAdjustAddForm addForm) {
        StockAdjustEntity stockAdjustEntity = SmartBeanUtil.copy(addForm, StockAdjustEntity.class);
        stockAdjustEntity.setAdjustNo(serialNumberService.generate(SerialNumberIdEnum.STOCK_ADJUST));
        stockAdjustEntity.setStatus(AdjustStatusEnum.PENDING.getValue());
        stockAdjustEntity.setDeletedFlag(Boolean.FALSE);
        stockAdjustManager.save(stockAdjustEntity);
        return ResponseDTO.ok();
    }

    /**
     * 更新库存调整单
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(StockAdjustUpdateForm updateForm) {
        StockAdjustEntity stockAdjustEntity = SmartBeanUtil.copy(updateForm, StockAdjustEntity.class);
        stockAdjustManager.update(stockAdjustEntity);
        return ResponseDTO.ok();
    }

    /**
     * 删除库存调整单（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long adjustId) {
        stockAdjustDao.batchUpdateDeleted(Collections.singletonList(adjustId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除库存调整单（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        stockAdjustDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 审核通过：仅「待审核」单据可审核；报损 / 报溢 在审核通过时真正变动库存
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> approve(StockAdjustApproveForm form) {
        StockAdjustEntity entity = stockAdjustDao.selectById(form.getAdjustId());
        if (entity == null) {
            return ResponseDTO.userErrorParam("调整单不存在");
        }
        if (!AdjustStatusEnum.PENDING.getValue().equals(entity.getStatus())) {
            return ResponseDTO.userErrorParam("仅待审核的调整单可审核");
        }
        Integer type = entity.getAdjustType();
        StockOperateForm op = buildOperateForm(entity);
        if (AdjustTypeEnum.LOSS.getValue().equals(type)) {
            stockOperateService.lossOut(op);
        } else if (AdjustTypeEnum.OVERFLOW.getValue().equals(type)) {
            stockOperateService.overflowIn(op);
        }
        // 盘点调整 / 规格转换 由对应业务流驱动库存，此处仅推进状态
        entity.setStatus(AdjustStatusEnum.COMPLETED.getValue());
        stockAdjustManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 驳回：仅「待审核」单据可驳回，不触发库存变动
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> reject(StockAdjustApproveForm form) {
        StockAdjustEntity entity = stockAdjustDao.selectById(form.getAdjustId());
        if (entity == null) {
            return ResponseDTO.userErrorParam("调整单不存在");
        }
        if (!AdjustStatusEnum.PENDING.getValue().equals(entity.getStatus())) {
            return ResponseDTO.userErrorParam("仅待审核的调整单可驳回");
        }
        entity.setStatus(AdjustStatusEnum.REJECTED.getValue());
        stockAdjustManager.update(entity);
        return ResponseDTO.ok();
    }

    private StockOperateForm buildOperateForm(StockAdjustEntity entity) {
        StockOperateForm op = new StockOperateForm();
        op.setProductId(entity.getProductId());
        op.setSkuId(entity.getSkuId());
        op.setWarehouseId(entity.getWarehouseId());
        op.setQuantity(entity.getQuantity());
        op.setWeight(entity.getWeight());
        op.setBizId(entity.getAdjustId());
        return op;
    }
}
