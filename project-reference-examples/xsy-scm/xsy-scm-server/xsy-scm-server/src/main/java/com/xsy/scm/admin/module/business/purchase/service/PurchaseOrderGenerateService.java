package com.xsy.scm.admin.module.business.purchase.service;

import com.xsy.scm.admin.module.business.purchase.constant.PurchaseItemStatusEnum;
import com.xsy.scm.admin.module.business.purchase.constant.SupplierSortModeEnum;
import com.xsy.scm.admin.module.business.purchase.dao.PurchaseGenerateDao;
import com.xsy.scm.admin.module.business.purchase.domain.bo.OrderRequireAggBO;
import com.xsy.scm.admin.module.business.purchase.domain.bo.ProductStockAggBO;
import com.xsy.scm.admin.module.business.purchase.domain.bo.ProductSupplierAggBO;
import com.xsy.scm.admin.module.business.purchase.domain.bo.PurchaseGenerateOrderBO;
import com.xsy.scm.admin.module.business.purchase.domain.entity.PurchaseItemEntity;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseGenerateForm;
import com.xsy.scm.admin.module.business.purchase.domain.vo.PurchaseGeneratePreviewItemVO;
import com.xsy.scm.admin.module.business.purchase.domain.vo.PurchaseGeneratePreviewVO;
import com.xsy.scm.admin.module.business.purchase.manager.PurchaseOrderGenerateManager;
import com.xsy.scm.base.common.domain.ResponseDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 采购单生成 Service（Q4：按时间段订单汇总生成采购单）
 *
 * <p>逻辑：汇总订单明细需求量 → 按(商品,规格)聚合 → 勾选库存抵扣时实时查询库存余额 →
 * 按商品默认供应商分组 → 每供应商落一张采购单（待接单态）。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class PurchaseOrderGenerateService {

    @Resource
    private PurchaseGenerateDao purchaseGenerateDao;

    @Resource
    private PurchaseOrderGenerateManager purchaseOrderGenerateManager;

    /**
     * 预览：按时间段汇总订单，按供应商分组展示计划采购量
     */
    public ResponseDTO<List<PurchaseGeneratePreviewVO>> preview(PurchaseGenerateForm form) {
        boolean calc = Boolean.TRUE.equals(form.getCalculateStock());
        List<OrderRequireAggBO> requireList = purchaseGenerateDao.aggregateRequireByProduct(form.getStartTime(), form.getEndTime());
        if (requireList.isEmpty()) {
            return ResponseDTO.ok(new ArrayList<>());
        }
        Map<Long, BigDecimal> stockMap = calc ? buildStockMap(requireList) : Collections.emptyMap();
        Map<Long, ProductSupplierAggBO> supplierMap = buildSupplierMap(requireList, form.getSupplierSortMode());

        Map<Long, PurchaseGeneratePreviewVO> groupMap = new LinkedHashMap<>();
        for (OrderRequireAggBO r : requireList) {
            ProductSupplierAggBO sup = supplierMap.get(r.getProductId());
            if (sup == null) {
                // 无供应商的商品无法落采购单，跳过
                continue;
            }
            BigDecimal stock = stockMap.getOrDefault(r.getProductId(), BigDecimal.ZERO);
            BigDecimal purchaseQty = calc
                    ? r.getRequireQuantity().subtract(stock).max(BigDecimal.ZERO)
                    : r.getRequireQuantity();
            PurchaseGeneratePreviewItemVO item = new PurchaseGeneratePreviewItemVO();
            item.setProductId(r.getProductId());
            item.setProductName(r.getProductName());
            item.setSkuId(r.getSkuId());
            item.setRequireQuantity(r.getRequireQuantity());
            item.setStockQuantity(stock);
            item.setPurchaseQuantity(purchaseQty);
            item.setUnitPrice(sup.getSupplyPrice());
            groupMap.computeIfAbsent(sup.getSupplierId(), k -> {
                PurchaseGeneratePreviewVO vo = new PurchaseGeneratePreviewVO();
                vo.setSupplierId(sup.getSupplierId());
                vo.setSupplierName(sup.getSupplierName());
                vo.setItems(new ArrayList<>());
                return vo;
            }).getItems().add(item);
        }
        return ResponseDTO.ok(new ArrayList<>(groupMap.values()));
    }

    /**
     * 生成采购单：按供应商分组落库（每供应商一张采购单，初始为「采购中」态，进入 采购中→部分收货→已完成 流转）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> generate(PurchaseGenerateForm form) {
        boolean calc = Boolean.TRUE.equals(form.getCalculateStock());
        List<OrderRequireAggBO> requireList = purchaseGenerateDao.aggregateRequireByProduct(form.getStartTime(), form.getEndTime());
        if (requireList.isEmpty()) {
            return ResponseDTO.ok("该时间段内没有可生成的采购需求");
        }
        Map<Long, BigDecimal> stockMap = calc ? buildStockMap(requireList) : Collections.emptyMap();
        Map<Long, ProductSupplierAggBO> supplierMap = buildSupplierMap(requireList, form.getSupplierSortMode());

        Map<Long, PurchaseGenerateOrderBO> orderMap = new LinkedHashMap<>();
        for (OrderRequireAggBO r : requireList) {
            ProductSupplierAggBO sup = supplierMap.get(r.getProductId());
            if (sup == null) {
                continue;
            }
            BigDecimal stock = stockMap.getOrDefault(r.getProductId(), BigDecimal.ZERO);
            BigDecimal purchaseQty = calc
                    ? r.getRequireQuantity().subtract(stock).max(BigDecimal.ZERO)
                    : r.getRequireQuantity();
            // 计算库存且库存已覆盖需求，则跳过该明细
            if (purchaseQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            PurchaseItemEntity item = new PurchaseItemEntity();
            item.setProductId(r.getProductId());
            item.setSkuId(r.getSkuId());
            item.setSupplierId(sup.getSupplierId());
            item.setRequireQuantity(r.getRequireQuantity());
            item.setPurchaseQuantity(purchaseQty);
            item.setReceivedQuantity(BigDecimal.ZERO);
            item.setUnitPrice(sup.getSupplyPrice());
            item.setStatus(PurchaseItemStatusEnum.PENDING.getValue());
            item.setDeletedFlag(Boolean.FALSE);

            PurchaseGenerateOrderBO orderBO = orderMap.computeIfAbsent(sup.getSupplierId(), k -> {
                PurchaseGenerateOrderBO bo = new PurchaseGenerateOrderBO();
                bo.setSupplierId(sup.getSupplierId());
                bo.setSupplierName(sup.getSupplierName());
                bo.setTotalAmount(BigDecimal.ZERO);
                bo.setItems(new ArrayList<>());
                return bo;
            });
            orderBO.getItems().add(item);
            orderBO.setTotalAmount(orderBO.getTotalAmount().add(purchaseQty.multiply(nullToZero(sup.getSupplyPrice()))));
        }
        if (orderMap.isEmpty()) {
            return ResponseDTO.ok("库存已覆盖全部需求，无需生成采购单");
        }
        purchaseOrderGenerateManager.saveOrders(new ArrayList<>(orderMap.values()));
        return ResponseDTO.ok("已生成 " + orderMap.size() + " 张采购单");
    }

    private BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private Map<Long, BigDecimal> buildStockMap(List<OrderRequireAggBO> requireList) {
        List<Long> productIds = requireList.stream().map(OrderRequireAggBO::getProductId).distinct().toList();
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ProductStockAggBO> stockList = purchaseGenerateDao.sumStockByProductIds(productIds);
        Map<Long, BigDecimal> map = new HashMap<>();
        for (ProductStockAggBO s : stockList) {
            map.put(s.getProductId(), nullToZero(s.getStockQuantity()));
        }
        return map;
    }

    /**
     * 按供应商分拣模式挑选商品的供应商（蔬东坡 17.1）
     *
     * <ul>
     *     <li>默认供应商 / 采购单生成后：优先取默认供应商（无默认取第一条）；</li>
     *     <li>按采购任务实时分配：不锁死默认供应商，取当前首要供应商，后续可由采购员改绑
     *         （见 {@code PurchaseItemService.reassignSupplier}）。</li>
     * </ul>
     */
    private Map<Long, ProductSupplierAggBO> buildSupplierMap(List<OrderRequireAggBO> requireList, Integer supplierSortMode) {
        List<Long> productIds = requireList.stream().map(OrderRequireAggBO::getProductId).distinct().toList();
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        boolean realtimeAssign = SupplierSortModeEnum.REALTIME_ASSIGN.getValue().equals(supplierSortMode);
        List<ProductSupplierAggBO> list = purchaseGenerateDao.listSupplierByProductIds(productIds);
        Map<Long, ProductSupplierAggBO> map = new HashMap<>();
        list.stream().collect(Collectors.groupingBy(ProductSupplierAggBO::getProductId))
                .forEach((pid, rows) -> {
                    ProductSupplierAggBO pick = realtimeAssign
                            ? rows.get(0)
                            : rows.stream().filter(r -> Boolean.TRUE.equals(r.getDefaultFlag())).findFirst().orElse(rows.get(0));
                    map.put(pid, pick);
                });
        return map;
    }
}
