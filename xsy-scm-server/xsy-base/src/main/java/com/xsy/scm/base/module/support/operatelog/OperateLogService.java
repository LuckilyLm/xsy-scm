package com.xsy.scm.base.module.support.operatelog;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import com.xsy.scm.base.common.code.UserErrorCode;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.util.XsyBeanUtil;
import com.xsy.scm.base.common.util.XsyPageUtil;
import com.xsy.scm.base.module.support.operatelog.domain.OperateLogEntity;
import com.xsy.scm.base.module.support.operatelog.domain.OperateLogQueryForm;
import com.xsy.scm.base.module.support.operatelog.domain.OperateLogVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 操作日志
 *
 */
@Service
public class OperateLogService {

    @Resource
    private OperateLogDao operateLogDao;

    /**
     * @description 分页查询
     */
    public ResponseDTO<PageResult<OperateLogVO>> queryByPage(OperateLogQueryForm queryForm) {
        Page page = XsyPageUtil.convert2PageQuery(queryForm);
        List<OperateLogEntity> logEntityList = operateLogDao.queryByPage(page, queryForm);
        PageResult<OperateLogVO> pageResult = XsyPageUtil.convert2PageResult(page, logEntityList, OperateLogVO.class);
        return ResponseDTO.ok(pageResult);
    }

    /**
     * 查询详情
     *
     * @param operateLogId
     * @return
     */
    public ResponseDTO<OperateLogVO> detail(Long operateLogId) {
        OperateLogEntity operateLogEntity = operateLogDao.selectById(operateLogId);
        if (operateLogEntity == null) {
            return ResponseDTO.error(UserErrorCode.DATA_NOT_EXIST);
        }
        OperateLogVO operateLogVO = XsyBeanUtil.copy(operateLogEntity, OperateLogVO.class);
        return ResponseDTO.ok(operateLogVO);
    }
}
