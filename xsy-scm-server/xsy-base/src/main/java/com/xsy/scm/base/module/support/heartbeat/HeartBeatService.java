package com.xsy.scm.base.module.support.heartbeat;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.util.XsyPageUtil;
import com.xsy.scm.base.module.support.heartbeat.domain.HeartBeatRecordQueryForm;
import com.xsy.scm.base.module.support.heartbeat.domain.HeartBeatRecordVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 心跳记录
 *
 */
@Slf4j
@Service
public class HeartBeatService {

    @Resource
    private HeartBeatRecordDao heartBeatRecordDao;

    public ResponseDTO<PageResult<HeartBeatRecordVO>> pageQuery(HeartBeatRecordQueryForm pageParam) {
        Page pageQueryInfo = XsyPageUtil.convert2PageQuery(pageParam);
        List<HeartBeatRecordVO> recordVOList = heartBeatRecordDao.pageQuery(pageQueryInfo, pageParam);
        PageResult<HeartBeatRecordVO> pageResult = XsyPageUtil.convert2PageResult(pageQueryInfo, recordVOList);
        return ResponseDTO.ok(pageResult);
    }
}
