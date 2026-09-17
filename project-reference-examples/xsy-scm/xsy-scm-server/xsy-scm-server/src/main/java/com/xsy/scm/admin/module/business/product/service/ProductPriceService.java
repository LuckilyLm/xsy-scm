package com.xsy.scm.admin.module.business.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.product.dao.ProductPriceDao;
import com.xsy.scm.admin.module.business.product.domain.entity.ProductPriceEntity;
import com.xsy.scm.admin.module.business.product.domain.form.ProductPriceAddForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductPriceQueryForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductPriceUpdateForm;
import com.xsy.scm.admin.module.business.product.domain.vo.ProductPriceVO;
import com.xsy.scm.admin.module.business.product.manager.ProductPriceManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import com.xsy.scm.admin.module.business.product.constant.PriceTypeEnum;
import com.xsy.scm.admin.module.business.product.domain.bo.ResolvedPriceBO;
import com.xsy.scm.base.common.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 商品价格 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class ProductPriceService {

    @Resource
    private ProductPriceDao priceDao;

    @Resource
    private ProductPriceManager priceManager;

    /**
     * 分页查询商品价格（按商品维度）
     */
    public ResponseDTO<PageResult<ProductPriceVO>> query(ProductPriceQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<ProductPriceVO> list = priceDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增商品价格
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(ProductPriceAddForm addForm) {
        ProductPriceEntity entity = SmartBeanUtil.copy(addForm, ProductPriceEntity.class);
        entity.setDeletedFlag(Boolean.FALSE);
        priceManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新商品价格
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(ProductPriceUpdateForm updateForm) {
        ProductPriceEntity entity = SmartBeanUtil.copy(updateForm, ProductPriceEntity.class);
        priceManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除商品价格（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long priceId) {
        priceDao.batchUpdateDeleted(Collections.singletonList(priceId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除商品价格（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        priceDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 按价格优先级解析成交价（不叠加）：
     * 协议价(4) > 促销价 > 分级价(2) > 时价(3) > 基础价(1)。
     * 促销价（营销模块）暂未实现，此处留空位；命中即返回，不再向下取价。
     *
     * @param productId        商品ID
     * @param skuId            规格ID，可空（按商品定价）
     * @param customerId       下单客户ID，可空
     * @param customerLevelId  客户分级ID，可空
     * @return 命中的成交价与取价类型
     */
    public ResolvedPriceBO resolvePrice(Long productId, Long skuId, Long customerId, Long customerLevelId) {
        ProductPriceEntity agreement = priceDao.selectAgreementPrice(productId, skuId, customerId);
        if (agreement != null) {
            return new ResolvedPriceBO(agreement.getPrice(), PriceTypeEnum.AGREEMENT);
        }
        // 促销价（PROMOTION）待营销模块接入
        ProductPriceEntity level = priceDao.selectLevelPrice(productId, skuId, customerLevelId);
        if (level != null) {
            return new ResolvedPriceBO(level.getPrice(), PriceTypeEnum.LEVEL);
        }
        ProductPriceEntity market = priceDao.selectMarketPrice(productId, skuId, LocalDateTime.now());
        if (market != null) {
            return new ResolvedPriceBO(market.getPrice(), PriceTypeEnum.MARKET);
        }
        ProductPriceEntity base = priceDao.selectBasePrice(productId, skuId);
        if (base != null) {
            return new ResolvedPriceBO(base.getPrice(), PriceTypeEnum.BASE);
        }
        throw new BusinessException("商品未配置有效价格（基础价缺失），无法下单");
    }
}
