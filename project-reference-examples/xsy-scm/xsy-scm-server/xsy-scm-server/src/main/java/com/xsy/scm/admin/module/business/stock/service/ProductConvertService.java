package com.xsy.scm.admin.module.business.stock.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.stock.constant.ConvertStatusEnum;
import com.xsy.scm.admin.module.business.stock.dao.ProductConvertDao;
import com.xsy.scm.admin.module.business.stock.dao.ProductConvertItemDao;
import com.xsy.scm.admin.module.business.stock.domain.entity.ProductConvertEntity;
import com.xsy.scm.admin.module.business.stock.domain.entity.ProductConvertItemEntity;
import com.xsy.scm.admin.module.business.stock.domain.form.ProductConvertAddForm;
import com.xsy.scm.admin.module.business.stock.domain.form.ProductConvertApproveForm;
import com.xsy.scm.admin.module.business.stock.domain.form.ProductConvertItemForm;
import com.xsy.scm.admin.module.business.stock.domain.form.ProductConvertQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.form.ProductConvertUpdateForm;
import com.xsy.scm.admin.module.business.stock.domain.form.StockOperateForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.ProductConvertDetailVO;
import com.xsy.scm.admin.module.business.stock.domain.vo.ProductConvertItemVO;
import com.xsy.scm.admin.module.business.stock.domain.vo.ProductConvertVO;
import com.xsy.scm.admin.module.business.stock.manager.ProductConvertManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import com.xsy.scm.base.module.support.serialnumber.constant.SerialNumberIdEnum;
import com.xsy.scm.base.module.support.serialnumber.service.SerialNumberService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 商品转换单 Service
 *
 * <p>对标蔬东坡 17.1 / 17.3：库内一品转多品，审核后生成「转换出 + 转换入」库存流水，
 * 由库存业务层保障成本与结存正确。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class ProductConvertService {

    /**
     * 金额精度（不含税，四舍五入到分）
     */
    private static final int AMOUNT_SCALE = 2;

    @Resource
    private ProductConvertDao productConvertDao;

    @Resource
    private ProductConvertItemDao productConvertItemDao;

    @Resource
    private ProductConvertManager productConvertManager;

    @Resource
    private StockOperateService stockOperateService;

    @Resource
    private SerialNumberService serialNumberService;

    /**
     * 分页查询商品转换单
     */
    public ResponseDTO<PageResult<ProductConvertVO>> query(ProductConvertQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<ProductConvertVO> list = productConvertDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 查询商品转换单详情（含明细）
     */
    public ResponseDTO<ProductConvertDetailVO> detail(Long convertId) {
        ProductConvertEntity entity = productConvertDao.selectById(convertId);
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("转换单不存在");
        }
        ProductConvertDetailVO detailVO = SmartBeanUtil.copy(entity, ProductConvertDetailVO.class);
        detailVO.setItems(productConvertItemDao.listByConvertId(convertId));
        return ResponseDTO.ok(detailVO);
    }

    /**
     * 新增商品转换单（待审核态），转换单号由编号生成器生成（ZHD + 日期 + 流水）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(ProductConvertAddForm addForm) {
        ResponseDTO<String> validateResult = validateItems(addForm.getItems());
        if (validateResult != null) {
            return validateResult;
        }
        ProductConvertEntity entity = SmartBeanUtil.copy(addForm, ProductConvertEntity.class);
        entity.setConvertNo(serialNumberService.generate(SerialNumberIdEnum.PRODUCT_CONVERT));
        entity.setStatus(ConvertStatusEnum.PENDING.getValue());
        entity.setDeletedFlag(Boolean.FALSE);
        productConvertManager.save(entity);

        productConvertManager.replaceItems(entity.getConvertId(), buildItems(entity.getConvertId(), addForm.getItems()));
        return ResponseDTO.ok();
    }

    /**
     * 更新商品转换单（仅待审核可改，明细整体覆盖）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(ProductConvertUpdateForm updateForm) {
        ProductConvertEntity existEntity = productConvertDao.selectById(updateForm.getConvertId());
        if (existEntity == null || Boolean.TRUE.equals(existEntity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("转换单不存在");
        }
        if (!ConvertStatusEnum.PENDING.getValue().equals(existEntity.getStatus())) {
            return ResponseDTO.userErrorParam("仅待审核的转换单可修改");
        }
        ResponseDTO<String> validateResult = validateItems(updateForm.getItems());
        if (validateResult != null) {
            return validateResult;
        }
        ProductConvertEntity entity = SmartBeanUtil.copy(updateForm, ProductConvertEntity.class);
        productConvertManager.update(entity);
        productConvertManager.replaceItems(entity.getConvertId(), buildItems(entity.getConvertId(), updateForm.getItems()));
        return ResponseDTO.ok();
    }

    /**
     * 审核通过：生成转换出 / 转换入库存流水（走库存业务层）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> approve(ProductConvertApproveForm form) {
        ProductConvertEntity entity = productConvertDao.selectById(form.getConvertId());
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("转换单不存在");
        }
        if (!ConvertStatusEnum.PENDING.getValue().equals(entity.getStatus())) {
            return ResponseDTO.userErrorParam("仅待审核的转换单可审核");
        }
        List<ProductConvertItemVO> items = productConvertItemDao.listByConvertId(form.getConvertId());
        if (items.isEmpty()) {
            return ResponseDTO.userErrorParam("转换明细为空，无法审核");
        }
        for (ProductConvertItemVO item : items) {
            stockOperateService.convertOut(buildOperateForm(item, entity.getWarehouseId(), true));
            stockOperateService.convertIn(buildOperateForm(item, entity.getWarehouseId(), false));
        }
        ProductConvertEntity updateEntity = new ProductConvertEntity();
        updateEntity.setConvertId(entity.getConvertId());
        updateEntity.setStatus(ConvertStatusEnum.COMPLETED.getValue());
        productConvertManager.update(updateEntity);
        return ResponseDTO.ok();
    }

    /**
     * 驳回：仅待审核可驳回，不触发库存变动
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> reject(ProductConvertApproveForm form) {
        ProductConvertEntity entity = productConvertDao.selectById(form.getConvertId());
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("转换单不存在");
        }
        if (!ConvertStatusEnum.PENDING.getValue().equals(entity.getStatus())) {
            return ResponseDTO.userErrorParam("仅待审核的转换单可驳回");
        }
        ProductConvertEntity updateEntity = new ProductConvertEntity();
        updateEntity.setConvertId(entity.getConvertId());
        updateEntity.setStatus(ConvertStatusEnum.REJECTED.getValue());
        productConvertManager.update(updateEntity);
        return ResponseDTO.ok();
    }

    /**
     * 删除商品转换单（逻辑删除，明细一并删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long convertId) {
        productConvertDao.batchUpdateDeleted(Collections.singletonList(convertId), Boolean.TRUE);
        productConvertItemDao.batchUpdateDeletedByConvertId(convertId);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除商品转换单（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        productConvertDao.batchUpdateDeleted(idList, Boolean.TRUE);
        for (Long convertId : idList) {
            productConvertItemDao.batchUpdateDeletedByConvertId(convertId);
        }
        return ResponseDTO.ok();
    }

    /**
     * 明细校验：源与目标不能相同；同一单据内禁止正向与逆向互转（蔬东坡 17.1 防错规则）
     */
    private ResponseDTO<String> validateItems(List<ProductConvertItemForm> items) {
        Set<String> pairs = new HashSet<>();
        for (ProductConvertItemForm item : items) {
            if (item.getSourceProductId().equals(item.getTargetProductId())
                    && Objects.equals(item.getSourceSkuId(), item.getTargetSkuId())) {
                return ResponseDTO.userErrorParam("原商品与目标商品不能相同");
            }
            String key = item.getSourceProductId() + "-" + item.getTargetProductId();
            String reverseKey = item.getTargetProductId() + "-" + item.getSourceProductId();
            if (pairs.contains(reverseKey)) {
                return ResponseDTO.userErrorParam("同一单据内不允许正向与逆向互相转换");
            }
            pairs.add(key);
        }
        return null;
    }

    /**
     * 构建明细实体，入库金额 = 入库数量 × 入库单价
     */
    private List<ProductConvertItemEntity> buildItems(Long convertId, List<ProductConvertItemForm> itemForms) {
        List<ProductConvertItemEntity> items = new ArrayList<>();
        for (ProductConvertItemForm itemForm : itemForms) {
            ProductConvertItemEntity item = SmartBeanUtil.copy(itemForm, ProductConvertItemEntity.class);
            item.setConvertId(convertId);
            BigDecimal targetAmount = BigDecimal.ZERO;
            if (itemForm.getTargetQuantity() != null && itemForm.getTargetUnitPrice() != null) {
                targetAmount = itemForm.getTargetQuantity().multiply(itemForm.getTargetUnitPrice())
                        .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            }
            item.setTargetAmount(targetAmount);
            items.add(item);
        }
        return items;
    }

    /**
     * 构建库存操作表单（isOut 为 true 时取原商品出库，否则取目标商品入库）
     */
    private StockOperateForm buildOperateForm(ProductConvertItemVO item, Long warehouseId, boolean isOut) {
        StockOperateForm op = new StockOperateForm();
        op.setWarehouseId(warehouseId);
        op.setBizId(item.getConvertId());
        if (isOut) {
            op.setProductId(item.getSourceProductId());
            op.setSkuId(item.getSourceSkuId());
            op.setQuantity(item.getSourceQuantity());
            op.setWeight(defaultZero(item.getSourceWeight()));
        } else {
            op.setProductId(item.getTargetProductId());
            op.setSkuId(item.getTargetSkuId());
            op.setQuantity(item.getTargetQuantity());
            op.setWeight(defaultZero(item.getTargetWeight()));
            op.setUnitPrice(item.getTargetUnitPrice());
        }
        return op;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
