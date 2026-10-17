package com.xsy.scm.mall.home.service;

import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.catalog.domain.vo.MallCategoryVO;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.catalog.service.MallCatalogService;
import com.xsy.scm.mall.home.dao.MallHomeDao;
import com.xsy.scm.mall.home.domain.form.MallHomeBlockForm;
import com.xsy.scm.mall.home.domain.vo.MallHomeBlockVO;
import com.xsy.scm.mall.home.domain.vo.MallHomePageVO;
import com.xsy.scm.mall.home.domain.vo.MallHomeReleaseVO;
import com.xsy.scm.mall.order.service.MallWarehouseService;
import com.xsy.scm.mall.promotion.service.MallPromotionService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.module.support.file.service.FileService;
import net.lab1024.sa.base.module.support.file.constant.FileFolderTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MallHomeProjectionService {
    private static final String PUBLIC_IMAGE_PREFIX = FileFolderTypeEnum.PUBLIC_IMAGE.getFolder();
    private final MallHomeDao homeDao;
    private final MallHomeLayoutService layoutService;
    private final MallHomeLayoutValidator validator;
    private final MallCatalogService catalogService;
    private final MallWarehouseService warehouseService;
    private final MallPromotionService promotionService;
    private final FileService fileService;

    @Transactional(readOnly = true)
    public MallHomePageVO published(RequestMallCustomer customer, Long warehouseId) {
        Long releaseId = homeDao.pointer().getCurrentReleaseId();
        if (releaseId == null) {
            return new MallHomePageVO(null, List.of());
        }
        MallHomeReleaseVO release = homeDao.release(releaseId);
        return release == null ? new MallHomePageVO(null, List.of())
                : project(releaseId, release.getContent(), customer, warehouseId);
    }

    @Transactional(readOnly = true)
    public MallHomePageVO draftPreview(Long customerId, Long warehouseId) {
        RequestMallCustomer customer = new RequestMallCustomer();
        customer.setCustomerId(customerId);
        Long selectedWarehouse = warehouseService.require(customerId, null, warehouseId);
        return project(null, homeDao.draft().getContent(), customer, selectedWarehouse);
    }

    private MallHomePageVO project(Long releaseId, String content, RequestMallCustomer customer, Long warehouseId) {
        List<MallHomeBlockForm> configured;
        try {
            configured = layoutService.parse(content);
        } catch (IllegalStateException invalidStoredLayout) {
            return new MallHomePageVO(releaseId, List.of());
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<MallHomeBlockForm> active = configured.stream().filter(Objects::nonNull)
                .filter(block -> block.getSortOrder() != null && Boolean.TRUE.equals(block.getEnabled())
                && (block.getCustomerIds() == null || block.getCustomerIds().isEmpty()
                        || block.getCustomerIds().contains(customer.getCustomerId()))
                && (block.getStartAt() == null || !now.isBefore(block.getStartAt()))
                && (block.getEndAt() == null || now.isBefore(block.getEndAt())))
                .sorted(Comparator.comparing(MallHomeBlockForm::getSortOrder)).toList();
        if (active.isEmpty()) {
            return new MallHomePageVO(releaseId, List.of());
        }
        List<Long> skuIds = active.stream().flatMap(block -> {
            List<Long> ids = new ArrayList<>(block.getSkuIds() == null ? List.of() : block.getSkuIds());
            if ("PRODUCT".equals(block.getLinkType())) {
                try {
                    ids.add(Long.parseLong(block.getLinkTarget()));
                } catch (RuntimeException invalidTarget) {
                    // The block is discarded below when its target cannot be resolved.
                }
            }
            return ids.stream();
        }).distinct().toList();
        Map<Long, MallProductVO> products = skuIds.isEmpty() ? Map.of()
                : catalogService.visibleProducts(customer, skuIds, null, warehouseId).stream()
                        .collect(Collectors.toMap(MallProductVO::getSkuId, Function.identity()));
        Set<Long> activityIds = active.stream().anyMatch(block -> "ACTIVITY".equals(block.getLinkType()))
                ? promotionService.activities(customer, warehouseId).stream().map(activity -> activity.getId())
                        .collect(Collectors.toSet()) : Set.of();
        Set<Long> categoryIds = new HashSet<>();
        if (active.stream().anyMatch(block -> "CATEGORY".equals(block.getLinkType()))) {
            for (MallCategoryVO category : catalogService.categories(customer)) {
                addCategoryIds(category, categoryIds);
            }
        }
        Map<String, String> imageUrls = new HashMap<>();
        List<String> imageKeys = active.stream().map(MallHomeBlockForm::getImageFileKey)
                .filter(key -> key != null && !key.isBlank() && key.startsWith(PUBLIC_IMAGE_PREFIX))
                .distinct().toList();
        if (!imageKeys.isEmpty()) {
            fileService.getFileList(imageKeys).stream()
                    .filter(file -> file.getFileUrl() != null && !file.getFileUrl().isBlank())
                    .forEach(file -> imageUrls.put(file.getFileKey(), file.getFileUrl()));
        }
        List<MallHomeBlockVO> blocks = new ArrayList<>();
        for (MallHomeBlockForm block : active) {
            if (block.getImageFileKey() != null && !block.getImageFileKey().isBlank()
                    && !imageUrls.containsKey(block.getImageFileKey())) {
                continue;
            }
            if (!targetVisible(block, products, categoryIds, activityIds)) {
                continue;
            }
            MallHomeBlockVO view = new MallHomeBlockVO();
            view.setKey(block.getKey());
            view.setType(block.getType());
            view.setSchemaVersion(block.getSchemaVersion());
            view.setTitle(block.getTitle());
            view.setSubtitle(block.getSubtitle());
            view.setText(block.getText());
            view.setImageUrl(imageUrls.get(block.getImageFileKey()));
            view.setLinkType(block.getLinkType());
            view.setLinkTarget(block.getLinkTarget());
            if ("RECOMMENDATION".equals(block.getType())) {
                if (block.getSkuIds() == null) continue;
                List<MallProductVO> visible = block.getSkuIds().stream().map(products::get)
                        .filter(product -> product != null).toList();
                if (visible.isEmpty()) continue;
                view.setProducts(visible);
            }
            blocks.add(view);
        }
        return new MallHomePageVO(releaseId, blocks);
    }

    private boolean targetVisible(MallHomeBlockForm block, Map<Long, MallProductVO> products,
            Set<Long> categoryIds, Set<Long> activityIds) {
        String type = block.getLinkType();
        if ("EXTERNAL_URL".equals(type)) {
            return validator.allowedExternalUrl(block.getLinkTarget());
        }
        if (!"PRODUCT".equals(type) && !"CATEGORY".equals(type) && !"ACTIVITY".equals(type)) {
            return true;
        }
        try {
            Long id = Long.valueOf(block.getLinkTarget());
            return switch (type) {
                case "PRODUCT" -> products.containsKey(id);
                case "CATEGORY" -> categoryIds.contains(id);
                default -> activityIds.contains(id);
            };
        } catch (RuntimeException invalidTarget) {
            return false;
        }
    }

    private static void addCategoryIds(MallCategoryVO category, Set<Long> ids) {
        ids.add(category.getCategoryId());
        for (MallCategoryVO child : category.getChildren()) {
            addCategoryIds(child, ids);
        }
    }
}
