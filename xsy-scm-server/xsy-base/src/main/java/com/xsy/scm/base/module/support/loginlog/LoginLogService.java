package com.xsy.scm.base.module.support.loginlog;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.common.util.XsyPageUtil;
import com.xsy.scm.base.module.support.loginlog.domain.LoginLogEntity;
import com.xsy.scm.base.module.support.loginlog.domain.LoginLogQueryForm;
import com.xsy.scm.base.module.support.loginlog.domain.LoginLogVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 登录日志
 *
 */
@Service
@Slf4j
public class LoginLogService {

    @Resource
    private LoginLogDao loginLogDao;

    /**
     * @description 分页查询
     */
    public ResponseDTO<PageResult<LoginLogVO>> queryByPage(LoginLogQueryForm queryForm) {
        Page page = XsyPageUtil.convert2PageQuery(queryForm);
        List<LoginLogVO> logList = loginLogDao.queryByPage(page, queryForm);
        PageResult<LoginLogVO> pageResult = XsyPageUtil.convert2PageResult(page, logList);
        return ResponseDTO.ok(pageResult);
    }

    /**
     * @description 添加
     */
    public void log(LoginLogEntity loginLogEntity) {
        try {
            loginLogDao.insert(loginLogEntity);
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 查询上一个登录记录
     *
     * @description 查询上一个登录记录
     */
    public LoginLogVO queryLastByUserId(Long userId, UserTypeEnum userTypeEnum, LoginLogResultEnum loginLogResultEnum) {
        return loginLogDao.queryLastByUserId(userId, userTypeEnum.getValue(), loginLogResultEnum.getValue());
    }

}
