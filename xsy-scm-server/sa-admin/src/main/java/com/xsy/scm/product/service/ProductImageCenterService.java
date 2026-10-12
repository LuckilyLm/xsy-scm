package com.xsy.scm.product.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.product.dao.ProductImageDao;
import com.xsy.scm.product.dao.ProductSpuDao;
import com.xsy.scm.product.domain.entity.ProductImageEntity;
import com.xsy.scm.product.domain.entity.ProductSpuEntity;
import com.xsy.scm.product.domain.form.ProductImageCenterForms;
import com.xsy.scm.product.domain.form.ProductImageForm;
import com.xsy.scm.product.domain.vo.ProductImageCenterVO;
import com.xsy.scm.product.domain.vo.ProductImageVO;
import com.xsy.scm.product.domain.vo.ProductUnboundImageVO;
import com.xsy.scm.product.manager.ProductAggregateValidator;
import com.xsy.scm.product.manager.ProductImageChangeSet;
import com.xsy.scm.product.manager.ProductImageSyncManager;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.module.support.file.constant.FileFolderTypeEnum;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.xsy.scm.product.constant.ProductErrorCode.IMAGE_NOT_OWNED;
import static com.xsy.scm.product.constant.ProductErrorCode.PRODUCT_NOT_FOUND;

/**
 * 图片中心：脱离商品编辑弹窗、按 SPU 单独维护图片的服务。所有写操作都收敛到 {@link ProductImageSyncManager#sync} 一条链路，从而复用其对 public/image/ 前缀、文件存在性与「每
 * SPU 至多一张主图」的既有校验，不在此处另写一套图片落库逻辑。
 */
@Service
@RequiredArgsConstructor
public class ProductImageCenterService {

    /** 未绑定图片池一次最多回多少张：这页是「挑图」而不是「翻库」，给到几百张已经够用。 */
    private static final int MAX_UNBOUND_IMAGES = 300;

    private final ProductSpuDao productSpuDao;
    private final ProductImageDao productImageDao;
    private final ProductImageSyncManager productImageSyncManager;
    private final ProductAggregateValidator productAggregateValidator;
    private final FileService fileService;

    public ProductImageCenterVO query(Long spuId) {
        ProductSpuEntity spu = requireSpu(spuId);
        List<ProductImageEntity> rows = productImageSyncManager.existing(spuId);
        Map<String,
                String> urls = fileService
                        .getFileList(rows.stream().map(ProductImageEntity::getFileKey).distinct().toList(),
                                SmartRequestUtil.getRequestUser())
                        .stream().filter(Objects::nonNull)
                        .collect(Collectors.toMap(FileVO::getFileKey, FileVO::getFileUrl, (a, b) -> a));
        List<ProductImageVO> images = rows.stream().map(i -> {
            ProductImageVO vo = new ProductImageVO();
            BeanUtils.copyProperties(i, vo);
            vo.setImageId(i.getId());
            vo.setFileUrl(urls.get(i.getFileKey()));
            return vo;
        }).toList();
        ProductImageCenterVO result = new ProductImageCenterVO();
        result.setSpuId(spu.getId());
        result.setSpuCode(spu.getSpuCode());
        result.setName(spu.getName());
        result.setImages(images);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchBind(ProductImageCenterForms.BatchBindForm form) {
        Map<Long, List<ProductImageCenterForms.BindItem>> bySpu = form.getItems().stream().collect(Collectors
                .groupingBy(ProductImageCenterForms.BindItem::getSpuId, LinkedHashMap::new, Collectors.toList()));
        for (var entry : bySpu.entrySet()) {
            Long spuId = entry.getKey();
            requireSpu(spuId);
            productImageSyncManager.sync(spuId, ProductImageChangeSet.between(productImageSyncManager.existing(spuId),
                    requestedWithBinds(spuId, entry.getValue())));
        }
    }

    /**
     * 换绑前按 SPU 汇总「现有 + 本次新增」的完整图片集合再过一遍校验。
     *
     * <p>
     * {@link ProductImageSyncManager#sync} 只保证 public/image/ 前缀与文件存在性；数量上限、主图至多一张与 fileKey 去重原先只在 {@code validateSpu}
     * 里，而图片中心不经那条路径，两张主图会直接顶到唯一索引上抛出未捕获的 {@code DuplicateKeyException}（500）。
     */
    private List<ProductImageForm> requestedWithBinds(Long spuId, List<ProductImageCenterForms.BindItem> binds) {
        List<ProductImageForm> requested = formsOf(spuId);
        for (var item : binds) {
            ProductImageForm add = new ProductImageForm();
            add.setFileKey(item.getFileKey());
            add.setPrimaryFlag(Boolean.TRUE.equals(item.getPrimaryFlag()));
            add.setSortOrder(item.getSortOrder() == null ? 0 : item.getSortOrder());
            requested.add(add);
        }
        productAggregateValidator.validateImages(requested);
        return requested;
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchRemove(ProductImageCenterForms.BatchRemoveForm form) {
        Long spuId = form.getSpuId();
        requireSpu(spuId);
        List<ProductImageEntity> existing = productImageSyncManager.existing(spuId);
        Set<Long> owned = existing.stream().map(ProductImageEntity::getId).collect(Collectors.toSet());
        for (Long id : form.getImageIds()) {
            if (!owned.contains(id))
                throw new ScmBusinessException(IMAGE_NOT_OWNED);
        }
        Set<Long> removing = new HashSet<>(form.getImageIds());
        List<ProductImageForm> requested = existing.stream().filter(e -> !removing.contains(e.getId()))
                .map(this::toForm).collect(Collectors.toList());
        productImageSyncManager.sync(spuId, ProductImageChangeSet.between(existing, requested));
    }

    @Transactional(rollbackFor = Exception.class)
    public void setPrimary(ProductImageCenterForms.SetPrimaryForm form) {
        Long spuId = form.getSpuId();
        requireSpu(spuId);
        List<ProductImageEntity> existing = productImageSyncManager.existing(spuId);
        boolean owned = existing.stream().anyMatch(e -> e.getId().equals(form.getImageId()));
        if (!owned)
            throw new ScmBusinessException(IMAGE_NOT_OWNED);
        List<ProductImageForm> requested = existing.stream().map(this::toForm)
                .peek(f -> f.setPrimaryFlag(f.getImageId().equals(form.getImageId()))).collect(Collectors.toList());
        productImageSyncManager.sync(spuId, ProductImageChangeSet.between(existing, requested));
    }

    @Transactional(rollbackFor = Exception.class)
    public void reorder(ProductImageCenterForms.ReorderForm form) {
        Long spuId = form.getSpuId();
        requireSpu(spuId);
        List<ProductImageEntity> existing = productImageSyncManager.existing(spuId);
        Map<Long, ProductImageEntity> byId = existing.stream()
                .collect(Collectors.toMap(ProductImageEntity::getId, Function.identity()));
        List<Long> ordered = form.getOrderedImageIds();
        if (ordered.size() != byId.size() || !new HashSet<>(ordered).equals(byId.keySet())) {
            throw new ScmBusinessException(IMAGE_NOT_OWNED);
        }
        Map<Long, ProductImageForm> forms = existing.stream().map(this::toForm)
                .collect(Collectors.toMap(ProductImageForm::getImageId, Function.identity()));
        for (int i = 0; i < ordered.size(); i++) {
            forms.get(ordered.get(i)).setSortOrder(i);
        }
        productImageSyncManager.sync(spuId, ProductImageChangeSet.between(existing, new ArrayList<>(forms.values())));
    }

    private List<ProductImageForm> formsOf(Long spuId) {
        return productImageSyncManager.existing(spuId).stream().map(this::toForm).collect(Collectors.toList());
    }

    /**
     * 已上传但还没挂到任何商品的公开图片，供图片中心直接挑图绑定。
     *
     * <p>
     * 只做展示，不改数据；绑定仍走 {@link #batchBind}，所以这里不做 public/image/ 之外的过滤 —— 能查出来的本来就只在那个目录。
     */
    public List<ProductUnboundImageVO> unboundImages(int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_UNBOUND_IMAGES);
        List<ProductUnboundImageVO> rows = productImageDao
                .selectUnboundPublicImages((Integer) FileFolderTypeEnum.PUBLIC_IMAGE.getValue(), capped);
        if (rows.isEmpty()) {
            return rows;
        }
        Map<String,
                String> urls = fileService
                        .getFileList(rows.stream().map(ProductUnboundImageVO::getFileKey).distinct().toList(),
                                SmartRequestUtil.getRequestUser())
                        .stream().filter(Objects::nonNull)
                        .collect(Collectors.toMap(FileVO::getFileKey, FileVO::getFileUrl, (a, b) -> a));
        rows.forEach(row -> row.setFileUrl(urls.get(row.getFileKey())));
        return rows;
    }

    private ProductImageForm toForm(ProductImageEntity e) {
        ProductImageForm f = new ProductImageForm();
        f.setImageId(e.getId());
        f.setVersion(e.getVersion());
        f.setFileKey(e.getFileKey());
        f.setFileName(e.getFileName());
        f.setFileSize(e.getFileSize());
        f.setPrimaryFlag(e.getPrimaryFlag());
        f.setSortOrder(e.getSortOrder());
        return f;
    }

    private ProductSpuEntity requireSpu(Long spuId) {
        ProductSpuEntity spu = productSpuDao.selectById(spuId);
        if (spu == null)
            throw new ScmBusinessException(PRODUCT_NOT_FOUND);
        return spu;
    }
}
