package com.xsy.scm.admin.module.business.stock.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.stock.dao.StockBalanceDao;
import com.xsy.scm.admin.module.business.stock.domain.form.StockBalanceQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.StockBalanceVO;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.util.SmartPageUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 库存余额 Service（只读）
 *
 * <p>余额由库存流水推导，禁止在此直接新增/修改/删除，写入统一走库存业务层。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class StockBalanceService {

    @Resource
    private StockBalanceDao stockBalanceDao;

    /**
     * 分页查询库存余额
     */
    public ResponseDTO<PageResult<StockBalanceVO>> query(StockBalanceQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<StockBalanceVO> list = stockBalanceDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }
}
