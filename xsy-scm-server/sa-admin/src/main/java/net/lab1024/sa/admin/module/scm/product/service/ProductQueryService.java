package net.lab1024.sa.admin.module.scm.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.dao.*;
import net.lab1024.sa.admin.module.scm.product.domain.entity.*;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSpuQueryForm;
import net.lab1024.sa.admin.module.scm.product.domain.vo.*;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static net.lab1024.sa.admin.module.scm.product.constant.ProductErrorCode.*;

@Service
@RequiredArgsConstructor
public class ProductQueryService {
    /** 单条 IN 的分片上限，见 {@link #enrich}。 */
    private static final int IN_BATCH = 500;
    private final ProductSpuDao spus;
    private final ProductSkuDao skus;
    private final ProductImageDao images;
    private final ProductCategoryService categories;
    private final ProductTagService tags;
    private final FileService files;

    public PageResult<ProductSpuVO> query(ProductSpuQueryForm form) {
        if (form.getSortItemList() != null) {
            Set<String> allowed = Set.of("spu_code", "name", "updated_at", "status");
            if (form.getSortItemList().stream().anyMatch(s -> !allowed.contains(s.getColumn())))
                throw new net.lab1024.sa.base.common.exception.BusinessException("不支持的商品排序字段");
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        if (page.orders().isEmpty()) page.addOrder(OrderItem.desc("updated_at"), OrderItem.desc("id"));
        var categoryRows = categories.all();
        var rows = spus.queryPage(page, form, form.getCategoryId() == null ? null : categories.descendantIds(form.getCategoryId(), categoryRows));
        var enriched = enrich(rows, categoryRows);
        return SmartPageUtil.convert2PageResult(page, new ArrayList<ProductSpuVO>(enriched));
    }

    public ProductSpuDetailVO detail(Long id) {
        var entity = spus.selectById(id);
        if (entity == null) throw new ScmBusinessException(PRODUCT_NOT_FOUND);
        return enrich(List.of(entity), categories.all()).getFirst();
    }

    private List<ProductSpuDetailVO> enrich(List<ProductSpuEntity> rows, List<ProductCategoryEntity> categoryRows) {
        if (rows.isEmpty()) return List.of();
        var ids = rows.stream().map(ProductSpuEntity::getId).toList();
        // 分片取关联数据：导出把 pageSize 强设为 10 万，整表 id 直接进一条 IN 会撞上
        // PostgreSQL 扩展协议单语句 65535 个绑定参数的上限（约 6.5 万商品处直接报错）。
        // 按 spuId 分片保证每个 SPU 完整落在同一片内，组内 sortOrder/id 次序不受影响。
        var skuRows = new ArrayList<ProductSkuEntity>();
        var imageRows = new ArrayList<ProductImageEntity>();
        for (var batch : Lists.partition(ids, IN_BATCH)) {
            skuRows.addAll(skus.selectList(new LambdaQueryWrapper<ProductSkuEntity>().in(ProductSkuEntity::getSpuId, batch)
                    .orderByAsc(ProductSkuEntity::getSortOrder, ProductSkuEntity::getId)));
            imageRows.addAll(images.selectList(new LambdaQueryWrapper<ProductImageEntity>().in(ProductImageEntity::getSpuId, batch)
                    .orderByAsc(ProductImageEntity::getSortOrder, ProductImageEntity::getId)));
        }
        var skuMap = skuRows.stream().collect(Collectors.groupingBy(ProductSkuEntity::getSpuId));
        var imageMap = imageRows.stream().collect(Collectors.groupingBy(ProductImageEntity::getSpuId));
        var tagMap = new LinkedHashMap<Long, List<ProductSpuTagVO>>();
        for (var batch : Lists.partition(ids, IN_BATCH))
            tags.bySpuIds(batch).forEach((spuId, bound) ->
                    tagMap.computeIfAbsent(spuId, k -> new ArrayList<>()).addAll(bound));
        Map<String, String> urls = new HashMap<>();
        for (var batch : Lists.partition(imageRows.stream().map(ProductImageEntity::getFileKey).distinct().toList(), IN_BATCH))
            files.getFileList(batch).stream().filter(Objects::nonNull)
                    .forEach(f -> urls.put(f.getFileKey(), f.getFileUrl()));
        Map<Long, String> names = categoryRows.stream().collect(Collectors.toMap(ProductCategoryEntity::getId, ProductCategoryEntity::getName));
        // 分类索引在循环外建一次：path(id, rows) 每次都会整表重建，放循环里是 O(页大小 × 分类总数)
        var categoryById = ProductCategoryService.indexById(categoryRows);
        List<ProductSpuDetailVO> result = new ArrayList<>();
        for (var row : rows) {
            var vo = new ProductSpuDetailVO();
            BeanUtils.copyProperties(row, vo);
            vo.setSpuId(row.getId());
            vo.setCategoryName(names.get(row.getCategoryId()));
            vo.setCategoryPath(ProductCategoryService.path(row.getCategoryId(), categoryById));
            vo.setTags(tagMap.getOrDefault(row.getId(), List.of()));
            List<ProductSkuVO> children = skuMap.getOrDefault(row.getId(), List.of()).stream().map(s -> {
                var child = new ProductSkuVO();
                BeanUtils.copyProperties(s, child);
                child.setSkuId(s.getId());
                return child;
            }).toList();
            vo.setSkuList(children);
            vo.setSkuCount(children.size());
            vo.setDefaultSku(children.stream().filter(s -> Boolean.TRUE.equals(s.getDefaultFlag())).findFirst().orElse(null));
            vo.setMinMarketPrice(children.stream().map(ProductSkuVO::getMarketPrice).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(null));
            vo.setMaxMarketPrice(children.stream().map(ProductSkuVO::getMarketPrice).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null));
            var pictures = imageMap.getOrDefault(row.getId(), List.of()).stream().map(i -> {
                var image = new ProductImageVO();
                BeanUtils.copyProperties(i, image);
                image.setImageId(i.getId());
                image.setFileUrl(urls.get(i.getFileKey()));
                return image;
            }).toList();
            vo.setImages(pictures);
            vo.setPrimaryImageUrl(pictures.stream().filter(i -> Boolean.TRUE.equals(i.getPrimaryFlag()))
                    .map(ProductImageVO::getFileUrl).filter(Objects::nonNull).findFirst().orElse(null));
            result.add(vo);
        }
        return result;
    }
}
