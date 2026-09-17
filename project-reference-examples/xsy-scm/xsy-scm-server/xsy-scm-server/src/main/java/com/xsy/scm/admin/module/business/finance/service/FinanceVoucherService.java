package com.xsy.scm.admin.module.business.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.finance.constant.VoucherEntryDirectionEnum;
import com.xsy.scm.admin.module.business.finance.constant.VoucherStatusEnum;
import com.xsy.scm.admin.module.business.finance.constant.VoucherSyncStatusEnum;
import com.xsy.scm.admin.module.business.finance.dao.ExternalConfigDao;
import com.xsy.scm.admin.module.business.finance.dao.FinanceVoucherDao;
import com.xsy.scm.admin.module.business.finance.dao.VoucherEntryDao;
import com.xsy.scm.admin.module.business.finance.domain.entity.ExternalConfigEntity;
import com.xsy.scm.admin.module.business.finance.domain.entity.FinanceVoucherEntity;
import com.xsy.scm.admin.module.business.finance.domain.entity.VoucherEntryEntity;
import com.xsy.scm.admin.module.business.finance.domain.form.VoucherAddForm;
import com.xsy.scm.admin.module.business.finance.domain.form.VoucherEntryForm;
import com.xsy.scm.admin.module.business.finance.domain.form.VoucherPushConfirmForm;
import com.xsy.scm.admin.module.business.finance.domain.form.VoucherPushForm;
import com.xsy.scm.admin.module.business.finance.domain.form.VoucherQueryForm;
import com.xsy.scm.admin.module.business.finance.domain.vo.FinanceVoucherDetailVO;
import com.xsy.scm.admin.module.business.finance.domain.vo.FinanceVoucherVO;
import com.xsy.scm.admin.module.business.finance.manager.FinanceVoucherManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import com.xsy.scm.base.module.support.serialnumber.constant.SerialNumberIdEnum;
import com.xsy.scm.base.module.support.serialnumber.service.SerialNumberService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会计凭证 Service
 *
 * <p>凭证由业务单据生成，**借贷必须平衡**；推送外部财务软件采用
 * 「提交同步（SYNCING）→ 对接层回执（SYNCED / FAILED）」的离线状态机。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class FinanceVoucherService {

    /**
     * 金额精度（不含税，HALF_UP 到分）
     */
    private static final int AMOUNT_SCALE = 2;

    @Resource
    private FinanceVoucherDao financeVoucherDao;

    @Resource
    private VoucherEntryDao voucherEntryDao;

    @Resource
    private FinanceVoucherManager financeVoucherManager;

    @Resource
    private ExternalConfigDao externalConfigDao;

    @Resource
    private SerialNumberService serialNumberService;

    /**
     * 分页查询会计凭证
     */
    public ResponseDTO<PageResult<FinanceVoucherVO>> query(VoucherQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<FinanceVoucherVO> list = financeVoucherDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 查询凭证详情（含分录）
     */
    public ResponseDTO<FinanceVoucherDetailVO> detail(Long voucherId) {
        FinanceVoucherEntity entity = financeVoucherDao.selectById(voucherId);
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("凭证不存在");
        }
        FinanceVoucherDetailVO detailVO = SmartBeanUtil.copy(entity, FinanceVoucherDetailVO.class);
        detailVO.setEntries(voucherEntryDao.listByVoucherId(voucherId));
        return ResponseDTO.ok(detailVO);
    }

    /**
     * 生成凭证：校验借贷平衡，按业务单幂等
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(VoucherAddForm addForm) {
        if (addForm.getBizId() != null) {
            Long count = financeVoucherDao.countByBiz(addForm.getBizType(), addForm.getBizId());
            if (count != null && count > 0) {
                return ResponseDTO.userErrorParam("该业务单已生成凭证，请勿重复生成");
            }
        }

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        List<VoucherEntryEntity> entries = new ArrayList<>();
        for (VoucherEntryForm entryForm : addForm.getEntries()) {
            BigDecimal amount = entryForm.getAmount();
            if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
                return ResponseDTO.userErrorParam("分录金额不能为负");
            }
            if (VoucherEntryDirectionEnum.DEBIT.getValue().equals(entryForm.getDirection())) {
                totalDebit = totalDebit.add(amount);
            } else if (VoucherEntryDirectionEnum.CREDIT.getValue().equals(entryForm.getDirection())) {
                totalCredit = totalCredit.add(amount);
            } else {
                return ResponseDTO.userErrorParam("借贷方向不合法");
            }
            VoucherEntryEntity entry = SmartBeanUtil.copy(entryForm, VoucherEntryEntity.class);
            entry.setAmount(amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
            entries.add(entry);
        }
        if (totalDebit.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseDTO.userErrorParam("凭证金额必须大于 0");
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            return ResponseDTO.userErrorParam("借贷不平衡：借方合计 " + totalDebit + "，贷方合计 " + totalCredit);
        }

        FinanceVoucherEntity voucher = new FinanceVoucherEntity();
        voucher.setVoucherNo(serialNumberService.generate(SerialNumberIdEnum.FINANCE_VOUCHER));
        voucher.setVoucherDate(addForm.getVoucherDate());
        voucher.setVoucherType(addForm.getVoucherType());
        voucher.setBizType(addForm.getBizType());
        voucher.setBizId(addForm.getBizId());
        voucher.setTotalDebit(totalDebit.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
        voucher.setTotalCredit(totalCredit.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
        voucher.setSyncStatus(VoucherSyncStatusEnum.UNSYNCED.getValue());
        voucher.setStatus(VoucherStatusEnum.GENERATED.getValue());
        voucher.setDeletedFlag(Boolean.FALSE);

        financeVoucherManager.saveWithEntries(voucher, entries);
        return ResponseDTO.ok();
    }

    /**
     * 作废凭证（已同步的凭证不可作废；幂等）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> invalidate(Long voucherId) {
        FinanceVoucherEntity entity = financeVoucherDao.selectById(voucherId);
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("凭证不存在");
        }
        if (VoucherStatusEnum.INVALID.getValue().equals(entity.getStatus())) {
            return ResponseDTO.ok();
        }
        if (VoucherSyncStatusEnum.SYNCED.getValue().equals(entity.getSyncStatus())) {
            return ResponseDTO.userErrorParam("已同步的凭证不可作废");
        }
        FinanceVoucherEntity updateEntity = new FinanceVoucherEntity();
        updateEntity.setVoucherId(voucherId);
        updateEntity.setStatus(VoucherStatusEnum.INVALID.getValue());
        financeVoucherManager.update(updateEntity);
        return ResponseDTO.ok();
    }

    /**
     * 提交同步：校验目标外部系统已启用，置为「同步中」等待对接层回执
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> push(VoucherPushForm pushForm) {
        FinanceVoucherEntity entity = financeVoucherDao.selectById(pushForm.getVoucherId());
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("凭证不存在");
        }
        if (VoucherStatusEnum.INVALID.getValue().equals(entity.getStatus())) {
            return ResponseDTO.userErrorParam("已作废的凭证不可推送");
        }
        if (VoucherSyncStatusEnum.SYNCED.getValue().equals(entity.getSyncStatus())) {
            return ResponseDTO.userErrorParam("凭证已同步，请勿重复推送");
        }
        ExternalConfigEntity config = externalConfigDao.getBySystemType(pushForm.getSystemType());
        if (config == null) {
            return ResponseDTO.userErrorParam("目标外部系统未启用，请先在外部系统配置中维护并启用");
        }
        FinanceVoucherEntity updateEntity = new FinanceVoucherEntity();
        updateEntity.setVoucherId(entity.getVoucherId());
        updateEntity.setSyncStatus(VoucherSyncStatusEnum.SYNCING.getValue());
        financeVoucherManager.update(updateEntity);
        return ResponseDTO.okMsg("已提交同步任务，等待外部系统回执");
    }

    /**
     * 同步结果回执：由外部对接层调用，置为同步成功 / 失败
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> confirmSync(VoucherPushConfirmForm confirmForm) {
        FinanceVoucherEntity entity = financeVoucherDao.selectById(confirmForm.getVoucherId());
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("凭证不存在");
        }
        FinanceVoucherEntity updateEntity = new FinanceVoucherEntity();
        updateEntity.setVoucherId(entity.getVoucherId());
        if (Boolean.TRUE.equals(confirmForm.getSuccess())) {
            updateEntity.setSyncStatus(VoucherSyncStatusEnum.SYNCED.getValue());
            updateEntity.setExternalNo(confirmForm.getExternalNo() != null
                    ? confirmForm.getExternalNo() : entity.getVoucherNo());
        } else {
            updateEntity.setSyncStatus(VoucherSyncStatusEnum.FAILED.getValue());
        }
        financeVoucherManager.update(updateEntity);
        return ResponseDTO.ok();
    }

    /**
     * 删除凭证（逻辑删除，分录一并删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long voucherId) {
        financeVoucherDao.batchUpdateDeleted(Collections.singletonList(voucherId), Boolean.TRUE);
        voucherEntryDao.batchUpdateDeletedByVoucherId(voucherId);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除凭证（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        financeVoucherDao.batchUpdateDeleted(idList, Boolean.TRUE);
        for (Long voucherId : idList) {
            voucherEntryDao.batchUpdateDeletedByVoucherId(voucherId);
        }
        return ResponseDTO.ok();
    }
}
