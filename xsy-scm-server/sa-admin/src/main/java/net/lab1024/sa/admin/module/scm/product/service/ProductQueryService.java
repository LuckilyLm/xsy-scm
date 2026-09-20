package net.lab1024.sa.admin.module.scm.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
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
    private final ProductSpuDao spus;
    private final ProductSkuDao skus;
    private final ProductImageDao images;
    private final ProductCategoryService categories;
    private final ProductTagService tags;
    private final FileService files;

    public PageResult<ProductSpuVO> query(ProductSpuQueryForm form) {
        if (form.getSortItemList()!=null) {
            Set<String> allowed=Set.of("spu_code","name","updated_at","status");
            if(form.getSortItemList().stream().anyMatch(s -> !allowed.contains(s.getColumn())))
                throw new net.lab1024.sa.base.common.exception.BusinessException("不支持的商品排序字段");
        }
        var page=SmartPageUtil.convert2PageQuery(form);
        if(page.orders().isEmpty()) page.addOrder(OrderItem.desc("updated_at"),OrderItem.desc("id"));
        var categoryRows=categories.all();
        var rows=spus.queryPage(page,form,form.getCategoryId()==null ? null : categories.descendantIds(form.getCategoryId(),categoryRows));
        var enriched=enrich(rows,categoryRows);
        return SmartPageUtil.convert2PageResult(page,new ArrayList<ProductSpuVO>(enriched));
    }
    public ProductSpuDetailVO detail(Long id) {
        var entity=spus.selectById(id);
        if (entity==null) throw new ScmBusinessException(PRODUCT_NOT_FOUND);
        return enrich(List.of(entity),categories.all()).getFirst();
    }
    private List<ProductSpuDetailVO> enrich(List<ProductSpuEntity> rows,List<ProductCategoryEntity> categoryRows) {
        if(rows.isEmpty()) return List.of();
        var ids=rows.stream().map(ProductSpuEntity::getId).toList();
        var skuRows=skus.selectList(new LambdaQueryWrapper<ProductSkuEntity>().in(ProductSkuEntity::getSpuId,ids)
                .orderByAsc(ProductSkuEntity::getSortOrder,ProductSkuEntity::getId));
        var imageRows=images.selectList(new LambdaQueryWrapper<ProductImageEntity>().in(ProductImageEntity::getSpuId,ids)
                .orderByAsc(ProductImageEntity::getSortOrder,ProductImageEntity::getId));
        var skuMap=skuRows.stream().collect(Collectors.groupingBy(ProductSkuEntity::getSpuId));
        var imageMap=imageRows.stream().collect(Collectors.groupingBy(ProductImageEntity::getSpuId));
        var tagMap=tags.bySpuIds(ids);
        Map<String,String> urls=new HashMap<>();
        files.getFileList(imageRows.stream().map(ProductImageEntity::getFileKey).distinct().toList())
                .stream().filter(Objects::nonNull).forEach(f -> urls.put(f.getFileKey(),f.getFileUrl()));
        Map<Long,String> names=categoryRows.stream().collect(Collectors.toMap(ProductCategoryEntity::getId,ProductCategoryEntity::getName));
        List<ProductSpuDetailVO> result=new ArrayList<>();
        for (var row:rows) {
            var vo=new ProductSpuDetailVO(); BeanUtils.copyProperties(row,vo); vo.setSpuId(row.getId());
            vo.setCategoryName(names.get(row.getCategoryId())); vo.setCategoryPath(ProductCategoryService.path(row.getCategoryId(),categoryRows));
            vo.setTags(tagMap.getOrDefault(row.getId(),List.of()));
            List<ProductSkuVO> children=skuMap.getOrDefault(row.getId(),List.of()).stream().map(s -> {
                var child=new ProductSkuVO(); BeanUtils.copyProperties(s,child); child.setSkuId(s.getId()); return child;
            }).toList();
            vo.setSkuList(children); vo.setSkuCount(children.size());
            vo.setDefaultSku(children.stream().filter(s -> Boolean.TRUE.equals(s.getDefaultFlag())).findFirst().orElse(null));
            vo.setMinMarketPrice(children.stream().map(ProductSkuVO::getMarketPrice).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(null));
            vo.setMaxMarketPrice(children.stream().map(ProductSkuVO::getMarketPrice).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null));
            var pictures=imageMap.getOrDefault(row.getId(),List.of()).stream().map(i -> {
                var image=new ProductImageVO(); BeanUtils.copyProperties(i,image); image.setImageId(i.getId());
                image.setFileUrl(urls.get(i.getFileKey())); return image;
            }).toList();
            vo.setImages(pictures); vo.setPrimaryImageUrl(pictures.stream().filter(i -> Boolean.TRUE.equals(i.getPrimaryFlag()))
                    .map(ProductImageVO::getFileUrl).filter(Objects::nonNull).findFirst().orElse(null));
            result.add(vo);
        }
        return result;
    }
}
