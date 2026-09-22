package net.lab1024.sa.admin.module.scm.product.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSpuDao;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductImageEntity;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductSpuEntity;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductImageCenterForms;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductImageForm;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductImageCenterVO;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductImageVO;
import net.lab1024.sa.admin.module.scm.product.manager.ProductImageChangeSet;
import net.lab1024.sa.admin.module.scm.product.manager.ProductImageSyncManager;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.lab1024.sa.admin.module.scm.product.constant.ProductErrorCode.*;

/**
 * 图片中心：脱离商品编辑弹窗、按 SPU 单独维护图片的服务。
 * 所有写操作都收敛到 {@link ProductImageSyncManager#sync} 一条链路，
 * 从而复用其对 public/image/ 前缀、文件存在性与「每 SPU 至多一张主图」的既有校验，
 * 不在此处另写一套图片落库逻辑。
 */
@Service
@RequiredArgsConstructor
public class ProductImageCenterService {
    private final ProductSpuDao spus;
    private final ProductImageSyncManager syncManager;
    private final FileService files;

    public ProductImageCenterVO query(Long spuId) {
        ProductSpuEntity spu = requireSpu(spuId);
        List<ProductImageEntity> rows = syncManager.existing(spuId);
        Map<String, String> urls = files.getFileList(rows.stream().map(ProductImageEntity::getFileKey).distinct().toList())
                .stream().filter(Objects::nonNull).collect(Collectors.toMap(FileVO::getFileKey, FileVO::getFileUrl, (a, b) -> a));
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
        Map<Long, List<ProductImageCenterForms.BindItem>> bySpu = form.getItems().stream()
                .collect(Collectors.groupingBy(ProductImageCenterForms.BindItem::getSpuId, LinkedHashMap::new, Collectors.toList()));
        for (var entry : bySpu.entrySet()) {
            Long spuId = entry.getKey();
            requireSpu(spuId);
            List<ProductImageForm> requested = formsOf(spuId);
            for (var item : entry.getValue()) {
                ProductImageForm add = new ProductImageForm();
                add.setFileKey(item.getFileKey());
                add.setPrimaryFlag(Boolean.TRUE.equals(item.getPrimaryFlag()));
                add.setSortOrder(item.getSortOrder() == null ? 0 : item.getSortOrder());
                requested.add(add);
            }
            syncManager.sync(spuId, ProductImageChangeSet.between(syncManager.existing(spuId), requested));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchRemove(ProductImageCenterForms.BatchRemoveForm form) {
        Long spuId = form.getSpuId();
        requireSpu(spuId);
        List<ProductImageEntity> existing = syncManager.existing(spuId);
        Set<Long> owned = existing.stream().map(ProductImageEntity::getId).collect(Collectors.toSet());
        for (Long id : form.getImageIds()) {
            if (!owned.contains(id)) throw new ScmBusinessException(IMAGE_NOT_OWNED);
        }
        Set<Long> removing = new HashSet<>(form.getImageIds());
        List<ProductImageForm> requested = existing.stream().filter(e -> !removing.contains(e.getId()))
                .map(this::toForm).collect(Collectors.toList());
        syncManager.sync(spuId, ProductImageChangeSet.between(existing, requested));
    }

    @Transactional(rollbackFor = Exception.class)
    public void setPrimary(ProductImageCenterForms.SetPrimaryForm form) {
        Long spuId = form.getSpuId();
        requireSpu(spuId);
        List<ProductImageEntity> existing = syncManager.existing(spuId);
        boolean owned = existing.stream().anyMatch(e -> e.getId().equals(form.getImageId()));
        if (!owned) throw new ScmBusinessException(IMAGE_NOT_OWNED);
        List<ProductImageForm> requested = existing.stream().map(this::toForm).peek(f ->
                f.setPrimaryFlag(f.getImageId().equals(form.getImageId()))).collect(Collectors.toList());
        syncManager.sync(spuId, ProductImageChangeSet.between(existing, requested));
    }

    @Transactional(rollbackFor = Exception.class)
    public void reorder(ProductImageCenterForms.ReorderForm form) {
        Long spuId = form.getSpuId();
        requireSpu(spuId);
        List<ProductImageEntity> existing = syncManager.existing(spuId);
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
        syncManager.sync(spuId, ProductImageChangeSet.between(existing, new ArrayList<>(forms.values())));
    }

    private List<ProductImageForm> formsOf(Long spuId) {
        return syncManager.existing(spuId).stream().map(this::toForm).collect(Collectors.toList());
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
        ProductSpuEntity spu = spus.selectById(spuId);
        if (spu == null) throw new ScmBusinessException(PRODUCT_NOT_FOUND);
        return spu;
    }
}
