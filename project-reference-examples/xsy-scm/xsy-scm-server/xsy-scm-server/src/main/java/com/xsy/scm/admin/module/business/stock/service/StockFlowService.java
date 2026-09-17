package com.xsy.scm.admin.module.business.stock.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.stock.dao.StockFlowDao;
import com.xsy.scm.admin.module.business.stock.domain.form.StockFlowQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.StockFlowVO;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.util.SmartPageUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 库存流水 Service（只读）
 *
 * <p>流水类表，不做逻辑删除，冲销使用反向流水；禁止通过接口新增/修改/删除。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class StockFlowService {

    @Resource
    private StockFlowDao stockFlowDao;

    /**
     * 分页查询库存流水
     */
    public ResponseDTO<PageResult<StockFlowVO>> query(StockFlowQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<StockFlowVO> list = stockFlowDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }
}
