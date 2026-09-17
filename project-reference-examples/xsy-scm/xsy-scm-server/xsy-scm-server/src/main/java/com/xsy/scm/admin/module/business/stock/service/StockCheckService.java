package com.xsy.scm.admin.module.business.stock.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.stock.dao.StockCheckDao;
import com.xsy.scm.admin.module.business.stock.domain.entity.StockCheckEntity;
import com.xsy.scm.admin.module.business.stock.domain.entity.StockCheckItemEntity;
import com.xsy.scm.admin.module.business.stock.domain.form.StockCheckAddForm;
import com.xsy.scm.admin.module.business.stock.domain.form.StockOperateForm;
import com.xsy.scm.admin.module.business.stock.domain.form.StockCheckQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.form.StockCheckUpdateForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.StockCheckVO;
import com.xsy.scm.admin.module.business.stock.constant.CheckStatusEnum;
import com.xsy.scm.admin.module.business.stock.dao.StockCheckItemDao;
import com.xsy.scm.admin.module.business.stock.manager.StockCheckManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * 库存盘点单 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class StockCheckService {

    @Resource
    private StockCheckDao checkDao;

    @Resource
    private StockCheckManager checkManager;

    @Resource
    private StockCheckItemDao checkItemDao;

    @Resource
    private StockOperateService stockOperateService;

    /**
     * 分页查询盘点单
     */
    public ResponseDTO<PageResult<StockCheckVO>> query(StockCheckQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<StockCheckVO> list = checkDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增盘点单，单号取 PDD + yyyyMMdd + 4 位自增主键
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(StockCheckAddForm addForm) {
        StockCheckEntity entity = SmartBeanUtil.copy(addForm, StockCheckEntity.class);
        entity.setDeletedFlag(Boolean.FALSE);
        checkManager.save(entity);
        String checkNo = "PDD" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%04d", entity.getCheckId());
        checkDao.updateCheckNo(entity.getCheckId(), checkNo);
        return ResponseDTO.ok();
    }

    /**
     * 更新盘点单
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(StockCheckUpdateForm updateForm) {
        StockCheckEntity entity = SmartBeanUtil.copy(updateForm, StockCheckEntity.class);
        checkManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除盘点单（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long checkId) {
        checkDao.batchUpdateDeleted(Collections.singletonList(checkId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除盘点单（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        checkDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 完成盘点：将各明细实盘数量 / 重量置为库存余额（盘点差异通过 checkAdjust 生成流水）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> complete(Long checkId) {
        StockCheckEntity check = checkDao.selectById(checkId);
        if (check == null) {
            return ResponseDTO.userErrorParam("盘点单不存在");
        }
        Integer status = check.getStatus();
        if (!CheckStatusEnum.PENDING.getValue().equals(status)
                && !CheckStatusEnum.CHECKING.getValue().equals(status)) {
            return ResponseDTO.userErrorParam("仅待盘点 / 盘点中的单据可完成");
        }
        List<StockCheckItemEntity> items = checkItemDao.queryByCheckId(checkId);
        for (StockCheckItemEntity item : items) {
            StockOperateForm op = new StockOperateForm();
            op.setProductId(item.getProductId());
            op.setSkuId(item.getSkuId());
            op.setQuantity(item.getActualQuantity());
            op.setWeight(item.getActualWeight());
            op.setBizId(checkId);
            stockOperateService.checkAdjust(op);
        }
        check.setStatus(CheckStatusEnum.COMPLETED.getValue());
        checkManager.update(check);
        return ResponseDTO.ok();
    }
}
