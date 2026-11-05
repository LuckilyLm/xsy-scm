package com.xsy.scm.base.module.support.heartbeat;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.xsy.scm.base.common.util.XsyBeanUtil;
import com.xsy.scm.base.module.support.heartbeat.core.HeartBeatRecord;
import com.xsy.scm.base.module.support.heartbeat.core.IHeartBeatRecordHandler;
import com.xsy.scm.base.module.support.heartbeat.domain.HeartBeatRecordEntity;
import org.springframework.stereotype.Service;

/**
 * 心跳记录
 *
 */
@Slf4j
@Service
public class HeartBeatRecordHandler implements IHeartBeatRecordHandler {

    @Resource
    private HeartBeatRecordDao heartBeatRecordDao;

    /**
     * 心跳日志处理方法
     *
     * @param heartBeatRecord
     */
    @Override
    public void handler(HeartBeatRecord heartBeatRecord) {
        HeartBeatRecordEntity heartBeatRecordEntity = XsyBeanUtil.copy(heartBeatRecord, HeartBeatRecordEntity.class);
        HeartBeatRecordEntity heartBeatRecordOld = heartBeatRecordDao.query(heartBeatRecordEntity);
        if (heartBeatRecordOld == null) {
            heartBeatRecordDao.insert(heartBeatRecordEntity);
        } else {
            heartBeatRecordDao.updateHeartBeatTimeById(heartBeatRecordOld.getHeartBeatRecordId(),
                heartBeatRecordEntity.getHeartBeatTime());
        }
    }

}
