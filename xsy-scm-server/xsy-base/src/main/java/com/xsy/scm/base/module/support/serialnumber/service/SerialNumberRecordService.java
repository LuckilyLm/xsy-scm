package com.xsy.scm.base.module.support.serialnumber.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.util.XsyPageUtil;
import com.xsy.scm.base.module.support.serialnumber.dao.SerialNumberRecordDao;
import com.xsy.scm.base.module.support.serialnumber.domain.SerialNumberRecordEntity;
import com.xsy.scm.base.module.support.serialnumber.domain.SerialNumberRecordQueryForm;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 单据序列号 记录
 *
 */
@Service
public class SerialNumberRecordService {

    @Resource
    private SerialNumberRecordDao serialNumberRecordDao;

    public PageResult<SerialNumberRecordEntity> query(SerialNumberRecordQueryForm queryForm) {
        Page page = XsyPageUtil.convert2PageQuery(queryForm);
        List<SerialNumberRecordEntity> recordList = serialNumberRecordDao.query(page, queryForm);
        return XsyPageUtil.convert2PageResult(page, recordList);
    }
}
