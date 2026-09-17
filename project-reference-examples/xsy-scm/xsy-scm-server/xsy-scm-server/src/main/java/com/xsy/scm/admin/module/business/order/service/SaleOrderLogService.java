package com.xsy.scm.admin.module.business.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.order.dao.SaleOrderLogDao;
import com.xsy.scm.admin.module.business.order.domain.entity.SaleOrderLogEntity;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderLogAddForm;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderLogQueryForm;
import com.xsy.scm.admin.module.business.order.domain.vo.SaleOrderLogVO;
import com.xsy.scm.admin.module.business.order.manager.SaleOrderLogManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 订单操作日志 Service
 *
 * <p>日志仅查询与写入，不支持修改与删除。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class SaleOrderLogService {

    @Resource
    private SaleOrderLogDao logDao;

    @Resource
    private SaleOrderLogManager logManager;

    /**
     * 分页查询订单操作日志（按订单维度）
     */
    public ResponseDTO<PageResult<SaleOrderLogVO>> query(SaleOrderLogQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<SaleOrderLogVO> list = logDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 写入订单操作日志
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(SaleOrderLogAddForm addForm) {
        SaleOrderLogEntity entity = SmartBeanUtil.copy(addForm, SaleOrderLogEntity.class);
        logManager.save(entity);
        return ResponseDTO.ok();
    }
}
