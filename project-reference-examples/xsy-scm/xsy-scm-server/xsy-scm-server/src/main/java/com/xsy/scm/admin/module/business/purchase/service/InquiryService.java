package com.xsy.scm.admin.module.business.purchase.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.purchase.constant.InquiryStatusEnum;
import com.xsy.scm.admin.module.business.purchase.dao.InquiryDao;
import com.xsy.scm.admin.module.business.purchase.dao.InquiryItemDao;
import com.xsy.scm.admin.module.business.purchase.dao.InquiryQuoteDao;
import com.xsy.scm.admin.module.business.purchase.domain.entity.InquiryEntity;
import com.xsy.scm.admin.module.business.purchase.domain.entity.InquiryItemEntity;
import com.xsy.scm.admin.module.business.purchase.domain.entity.InquiryQuoteEntity;
import com.xsy.scm.admin.module.business.purchase.domain.form.InquiryAddForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.InquiryItemForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.InquiryQueryForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.InquiryQuoteForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.InquiryQuoteItemForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.InquiryStatusForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.InquiryUpdateForm;
import com.xsy.scm.admin.module.business.purchase.domain.vo.InquiryCompareItemVO;
import com.xsy.scm.admin.module.business.purchase.domain.vo.InquiryCompareQuoteVO;
import com.xsy.scm.admin.module.business.purchase.domain.vo.InquiryCompareVO;
import com.xsy.scm.admin.module.business.purchase.domain.vo.InquiryDetailVO;
import com.xsy.scm.admin.module.business.purchase.domain.vo.InquiryItemVO;
import com.xsy.scm.admin.module.business.purchase.domain.vo.InquiryQuoteVO;
import com.xsy.scm.admin.module.business.purchase.domain.vo.InquiryVO;
import com.xsy.scm.admin.module.business.purchase.manager.InquiryManager;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采购询价报价 Service
 *
 * <p>对标蔬东坡 16.5 / 17.3：多供应商报价、供应商评分、平均价 / 中位价对比。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class InquiryService {

    /**
     * 价格计算精度（与单价 DECIMAL(18,4) 一致）
     */
    private static final int PRICE_SCALE = 4;

    @Resource
    private InquiryDao inquiryDao;

    @Resource
    private InquiryItemDao inquiryItemDao;

    @Resource
    private InquiryQuoteDao inquiryQuoteDao;

    @Resource
    private InquiryManager inquiryManager;

    @Resource
    private SerialNumberService serialNumberService;

    /**
     * 分页查询询价单
     */
    public ResponseDTO<PageResult<InquiryVO>> query(InquiryQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<InquiryVO> list = inquiryDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 查询询价单详情（含明细）
     */
    public ResponseDTO<InquiryDetailVO> detail(Long inquiryId) {
        InquiryEntity entity = inquiryDao.selectById(inquiryId);
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("询价单不存在");
        }
        InquiryDetailVO detailVO = SmartBeanUtil.copy(entity, InquiryDetailVO.class);
        detailVO.setItems(inquiryItemDao.listByInquiryId(inquiryId));
        return ResponseDTO.ok(detailVO);
    }

    /**
     * 方案对比：按商品计算平均价、中位价，并给出各供应商报价与差额
     */
    public ResponseDTO<InquiryCompareVO> compare(Long inquiryId) {
        InquiryEntity entity = inquiryDao.selectById(inquiryId);
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("询价单不存在");
        }
        List<InquiryItemVO> items = inquiryItemDao.listByInquiryId(inquiryId);
        List<InquiryQuoteVO> quotes = inquiryQuoteDao.listByInquiryId(inquiryId);

        InquiryCompareVO compareVO = new InquiryCompareVO();
        compareVO.setInquiryId(entity.getInquiryId());
        compareVO.setInquiryNo(entity.getInquiryNo());
        compareVO.setInquiryName(entity.getInquiryName());

        List<InquiryCompareItemVO> compareItems = new ArrayList<>();
        for (InquiryItemVO item : items) {
            compareItems.add(buildCompareItem(item, quotes));
        }
        compareVO.setItems(compareItems);
        return ResponseDTO.ok(compareVO);
    }

    /**
     * 新增询价单，询价单号由编号生成器生成（XJD + 日期 + 流水）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(InquiryAddForm addForm) {
        if (addForm.getValidEnd().isBefore(addForm.getValidStart())) {
            return ResponseDTO.userErrorParam("询价有效结束时间不能早于开始时间");
        }
        InquiryEntity entity = SmartBeanUtil.copy(addForm, InquiryEntity.class);
        entity.setInquiryNo(serialNumberService.generate(SerialNumberIdEnum.INQUIRY));
        entity.setStatus(InquiryStatusEnum.PENDING.getValue());
        entity.setDeletedFlag(Boolean.FALSE);
        inquiryManager.save(entity);

        inquiryManager.replaceItems(entity.getInquiryId(), buildItems(entity.getInquiryId(), addForm.getItems()));
        return ResponseDTO.ok();
    }

    /**
     * 更新询价单（已完成 / 已取消不可修改）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(InquiryUpdateForm updateForm) {
        InquiryEntity existEntity = inquiryDao.selectById(updateForm.getInquiryId());
        if (existEntity == null || Boolean.TRUE.equals(existEntity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("询价单不存在");
        }
        if (isClosed(existEntity.getStatus())) {
            return ResponseDTO.userErrorParam("已完成 / 已取消的询价单不可修改");
        }
        if (updateForm.getValidEnd().isBefore(updateForm.getValidStart())) {
            return ResponseDTO.userErrorParam("询价有效结束时间不能早于开始时间");
        }
        InquiryEntity entity = SmartBeanUtil.copy(updateForm, InquiryEntity.class);
        inquiryManager.update(entity);
        inquiryManager.replaceItems(entity.getInquiryId(), buildItems(entity.getInquiryId(), updateForm.getItems()));
        return ResponseDTO.ok();
    }

    /**
     * 供应商报价（同一明细 + 供应商重复报价时覆盖更新）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> quote(InquiryQuoteForm quoteForm) {
        InquiryEntity inquiry = inquiryDao.selectById(quoteForm.getInquiryId());
        if (inquiry == null || Boolean.TRUE.equals(inquiry.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("询价单不存在");
        }
        if (isClosed(inquiry.getStatus())) {
            return ResponseDTO.userErrorParam("询价单已结束，无法报价");
        }
        if (inquiry.getValidEnd() != null && LocalDateTime.now().isAfter(inquiry.getValidEnd())) {
            return ResponseDTO.userErrorParam("已过询价有效期，无法报价");
        }
        List<InquiryItemVO> items = inquiryItemDao.listByInquiryId(quoteForm.getInquiryId());
        Set<Long> itemIds = items.stream().map(InquiryItemVO::getItemId).collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        for (InquiryQuoteItemForm quoteItem : quoteForm.getItems()) {
            if (!itemIds.contains(quoteItem.getItemId())) {
                return ResponseDTO.userErrorParam("询价明细不存在：" + quoteItem.getItemId());
            }
            InquiryQuoteEntity existQuote = inquiryQuoteDao.getByItemAndSupplier(quoteItem.getItemId(), quoteForm.getSupplierId());
            if (existQuote == null) {
                InquiryQuoteEntity quote = new InquiryQuoteEntity();
                quote.setInquiryId(quoteForm.getInquiryId());
                quote.setItemId(quoteItem.getItemId());
                quote.setSupplierId(quoteForm.getSupplierId());
                quote.setQuotePrice(quoteItem.getQuotePrice());
                quote.setScore(quoteItem.getScore());
                quote.setQuoteTime(now);
                quote.setDeletedFlag(Boolean.FALSE);
                inquiryManager.saveQuote(quote);
            } else {
                existQuote.setQuotePrice(quoteItem.getQuotePrice());
                existQuote.setScore(quoteItem.getScore());
                existQuote.setQuoteTime(now);
                inquiryManager.updateQuote(existQuote);
            }
        }

        // 首次报价后，询价单由「待报价」转为「报价中」
        if (InquiryStatusEnum.PENDING.getValue().equals(inquiry.getStatus())) {
            InquiryEntity updateEntity = new InquiryEntity();
            updateEntity.setInquiryId(inquiry.getInquiryId());
            updateEntity.setStatus(InquiryStatusEnum.QUOTING.getValue());
            inquiryManager.update(updateEntity);
        }
        return ResponseDTO.ok();
    }

    /**
     * 变更询价单状态（完成 / 取消）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> changeStatus(InquiryStatusForm statusForm) {
        InquiryEntity entity = inquiryDao.selectById(statusForm.getInquiryId());
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("询价单不存在");
        }
        if (!InquiryStatusEnum.COMPLETED.getValue().equals(statusForm.getStatus())
                && !InquiryStatusEnum.CANCELLED.getValue().equals(statusForm.getStatus())) {
            return ResponseDTO.userErrorParam("仅支持完成或取消");
        }
        if (isClosed(entity.getStatus())) {
            return ResponseDTO.userErrorParam("询价单已结束");
        }
        InquiryEntity updateEntity = new InquiryEntity();
        updateEntity.setInquiryId(entity.getInquiryId());
        updateEntity.setStatus(statusForm.getStatus());
        inquiryManager.update(updateEntity);
        return ResponseDTO.ok();
    }

    /**
     * 删除询价单（逻辑删除，明细一并删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long inquiryId) {
        inquiryDao.batchUpdateDeleted(Collections.singletonList(inquiryId), Boolean.TRUE);
        inquiryItemDao.batchUpdateDeletedByInquiryId(inquiryId);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除询价单（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        inquiryDao.batchUpdateDeleted(idList, Boolean.TRUE);
        for (Long inquiryId : idList) {
            inquiryItemDao.batchUpdateDeletedByInquiryId(inquiryId);
        }
        return ResponseDTO.ok();
    }

    /**
     * 构建询价明细实体
     */
    private List<InquiryItemEntity> buildItems(Long inquiryId, List<InquiryItemForm> itemForms) {
        List<InquiryItemEntity> items = new ArrayList<>();
        for (InquiryItemForm itemForm : itemForms) {
            InquiryItemEntity item = SmartBeanUtil.copy(itemForm, InquiryItemEntity.class);
            item.setInquiryId(inquiryId);
            items.add(item);
        }
        return items;
    }

    /**
     * 构建单个商品的对比结果
     */
    private InquiryCompareItemVO buildCompareItem(InquiryItemVO item, List<InquiryQuoteVO> allQuotes) {
        List<InquiryQuoteVO> itemQuotes = allQuotes.stream()
                .filter(quote -> item.getItemId().equals(quote.getItemId()))
                .collect(Collectors.toList());

        List<BigDecimal> prices = itemQuotes.stream()
                .map(InquiryQuoteVO::getQuotePrice)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());

        BigDecimal avgPrice = null;
        BigDecimal medianPrice = null;
        if (!prices.isEmpty()) {
            BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            avgPrice = sum.divide(BigDecimal.valueOf(prices.size()), PRICE_SCALE, RoundingMode.HALF_UP);
            medianPrice = calcMedian(prices);
        }

        List<InquiryCompareQuoteVO> compareQuotes = new ArrayList<>();
        for (InquiryQuoteVO quote : itemQuotes) {
            InquiryCompareQuoteVO compareQuote = SmartBeanUtil.copy(quote, InquiryCompareQuoteVO.class);
            if (quote.getQuotePrice() != null && avgPrice != null) {
                compareQuote.setAvgDiff(quote.getQuotePrice().subtract(avgPrice).abs());
            }
            if (quote.getQuotePrice() != null && medianPrice != null) {
                compareQuote.setMedianDiff(quote.getQuotePrice().subtract(medianPrice).abs());
            }
            compareQuotes.add(compareQuote);
        }

        InquiryCompareItemVO compareItem = SmartBeanUtil.copy(item, InquiryCompareItemVO.class);
        compareItem.setAvgPrice(avgPrice);
        compareItem.setMedianPrice(medianPrice);
        compareItem.setQuotes(compareQuotes);
        return compareItem;
    }

    /**
     * 计算中位价：奇数取中间值；偶数取中间两个的平均值
     */
    private BigDecimal calcMedian(List<BigDecimal> sortedPrices) {
        int size = sortedPrices.size();
        if (size == 0) {
            return null;
        }
        int middle = size / 2;
        if (size % 2 == 1) {
            return sortedPrices.get(middle);
        }
        return sortedPrices.get(middle - 1).add(sortedPrices.get(middle))
                .divide(BigDecimal.valueOf(2), PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 是否为终态（已完成 / 已取消）
     */
    private boolean isClosed(Integer status) {
        return InquiryStatusEnum.COMPLETED.getValue().equals(status)
                || InquiryStatusEnum.CANCELLED.getValue().equals(status);
    }
}
