package com.xsy.scm.base.module.support.serialnumber.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.util.SmartPageUtil;
import com.xsy.scm.base.module.support.serialnumber.dao.SerialNumberRecordDao;
import com.xsy.scm.base.module.support.serialnumber.domain.SerialNumberRecordEntity;
import com.xsy.scm.base.module.support.serialnumber.domain.SerialNumberRecordQueryForm;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 单据序列号 记录
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-03-25 21:46:07
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Service
public class SerialNumberRecordService {

    @Resource
    private SerialNumberRecordDao serialNumberRecordDao;

    public PageResult<SerialNumberRecordEntity> query(SerialNumberRecordQueryForm queryForm) {
        Page page = SmartPageUtil.convert2PageQuery(queryForm);
        List<SerialNumberRecordEntity> recordList = serialNumberRecordDao.query(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, recordList);
    }
}
