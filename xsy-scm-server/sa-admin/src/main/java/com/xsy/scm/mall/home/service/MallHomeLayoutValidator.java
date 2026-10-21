package com.xsy.scm.mall.home.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.mall.home.constant.MallHomeBlockType;
import com.xsy.scm.mall.home.constant.MallHomeLinkType;
import com.xsy.scm.mall.home.domain.form.MallHomeBlockForm;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.service.ProductCategoryService;
import com.xsy.scm.promotion.service.PromotionActivityService;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.module.support.file.constant.FileFolderTypeEnum;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static com.xsy.scm.mall.home.constant.MallHomeErrorCode.INVALID_IMAGE;
import static com.xsy.scm.mall.home.constant.MallHomeErrorCode.INVALID_LAYOUT;
import static com.xsy.scm.mall.home.constant.MallHomeErrorCode.INVALID_LINK;

@Component
@RequiredArgsConstructor
public class MallHomeLayoutValidator {
    private final CustomerService customerService;
    private final ScmDataScopeService scopeService;
    private final ProductSkuOptionDao skuOptionDao;
    private final ProductCategoryService categoryService;
    private final PromotionActivityService activityService;
    private final FileService fileService;

    @Value("${scm.mall.home.allowed-external-hosts:}")
    private String allowedExternalHosts;

    public void validate(List<MallHomeBlockForm> blocks) {
        if (blocks == null || blocks.size() > 30) {
            throw new ScmBusinessException(INVALID_LAYOUT);
        }
        Set<String> keys = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        Set<Long> referencedSkus = new HashSet<>();
        Set<Long> audienceIds = new HashSet<>();
        Set<String> imageKeys = new HashSet<>();
        ScmValueScope audienceScope = scopeService.resolve().getCustomerSellerScope();
        for (MallHomeBlockForm block : blocks) {
            if (block == null || block.getKey() == null || !block.getKey().matches("[A-Za-z0-9_-]{1,64}")
                    || !keys.add(block.getKey()) || block.getSortOrder() == null || !positions.add(block.getSortOrder())
                    || !Objects.equals(block.getSchemaVersion(), 1) || block.getEnabled() == null
                    || block.getType() == null) {
                throw new ScmBusinessException(INVALID_LAYOUT);
            }
            MallHomeBlockType type;
            try {
                type = MallHomeBlockType.valueOf(block.getType());
            } catch (IllegalArgumentException invalidType) {
                throw new ScmBusinessException(INVALID_LAYOUT);
            }
            if (block.getStartAt() != null && block.getEndAt() != null
                    && !block.getEndAt().isAfter(block.getStartAt())) {
                throw new ScmBusinessException(INVALID_LAYOUT);
            }
            if (block.getSortOrder() < 0 || block.getSortOrder() > 1000) {
                throw new ScmBusinessException(INVALID_LAYOUT);
            }
            validateAudience(block.getCustomerIds(), audienceScope, audienceIds);
            validateContent(type, block);
            validateLink(block);
            if (block.getImageFileKey() != null && !block.getImageFileKey().isBlank()) {
                imageKeys.add(block.getImageFileKey());
            }
            if (block.getSkuIds() != null) {
                referencedSkus.addAll(block.getSkuIds());
            }
            if (MallHomeLinkType.PRODUCT.name().equals(block.getLinkType())) {
                referencedSkus.add(parseId(block.getLinkTarget()));
            }
        }
        if (!referencedSkus.isEmpty() && referencedSkus.size() > 100) {
            throw new ScmBusinessException(INVALID_LAYOUT);
        }
        if (!referencedSkus.isEmpty()
                && skuOptionDao.selectByIds(referencedSkus.stream().toList()).size() != referencedSkus.size()) {
            throw new ScmBusinessException(INVALID_LINK);
        }
        if (!imageKeys.isEmpty()) {
            String prefix = FileFolderTypeEnum.PUBLIC_IMAGE.getFolder();
            if (imageKeys.stream().anyMatch(key -> !key.startsWith(prefix))
                    || fileService.getFileMetadata(imageKeys.stream().toList()).size() != imageKeys.size()) {
                throw new ScmBusinessException(INVALID_IMAGE);
            }
        }
        audienceIds.forEach(customerService::requireInCurrentScope);
    }

    private static void validateAudience(List<Long> customerIds, ScmValueScope scope, Set<Long> audienceIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            if (!scope.isAll()) {
                throw new ScmBusinessException(INVALID_LAYOUT);
            }
            return;
        }
        if (customerIds.size() > 100 || customerIds.stream().anyMatch(id -> id == null || id <= 0)
                || customerIds.stream().distinct().count() != customerIds.size()) {
            throw new ScmBusinessException(INVALID_LAYOUT);
        }
        audienceIds.addAll(customerIds);
    }

    private static void validateContent(MallHomeBlockType type, MallHomeBlockForm block) {
        if (block.getSkuIds() != null && (block.getSkuIds().stream().anyMatch(id -> id == null || id <= 0)
                || block.getSkuIds().stream().distinct().count() != block.getSkuIds().size())) {
            throw new ScmBusinessException(INVALID_LAYOUT);
        }
        if (type != MallHomeBlockType.RECOMMENDATION && block.getSkuIds() != null && !block.getSkuIds().isEmpty()) {
            throw new ScmBusinessException(INVALID_LAYOUT);
        }
        switch (type) {
            case BANNER -> {
                if (blank(block.getImageFileKey()))
                    throw new ScmBusinessException(INVALID_IMAGE);
            }
            case SHORTCUT -> {
                if (blank(block.getTitle()))
                    throw new ScmBusinessException(INVALID_LAYOUT);
                if (block.getLinkType() == null || MallHomeLinkType.NONE.name().equals(block.getLinkType())) {
                    throw new ScmBusinessException(INVALID_LINK);
                }
            }
            case RECOMMENDATION -> {
                if (blank(block.getTitle()) || block.getSkuIds() == null || block.getSkuIds().isEmpty()) {
                    throw new ScmBusinessException(INVALID_LAYOUT);
                }
                if (block.getLinkType() != null && !MallHomeLinkType.NONE.name().equals(block.getLinkType())) {
                    throw new ScmBusinessException(INVALID_LINK);
                }
            }
            case ACTIVITY_TOPIC -> {
                if (blank(block.getTitle()) || !MallHomeLinkType.ACTIVITY.name().equals(block.getLinkType())) {
                    throw new ScmBusinessException(INVALID_LAYOUT);
                }
            }
            case ANNOUNCEMENT -> {
                if (blank(block.getText()))
                    throw new ScmBusinessException(INVALID_LAYOUT);
            }
        }
    }

    private void validateLink(MallHomeBlockForm block) {
        MallHomeLinkType link;
        try {
            link = block.getLinkType() == null ? MallHomeLinkType.NONE : MallHomeLinkType.valueOf(block.getLinkType());
        } catch (IllegalArgumentException invalidType) {
            throw new ScmBusinessException(INVALID_LINK);
        }
        switch (link) {
            case NONE, COUPONS, SEARCH -> {
                if (!blank(block.getLinkTarget()))
                    throw new ScmBusinessException(INVALID_LINK);
            }
            case PRODUCT -> parseId(block.getLinkTarget());
            case CATEGORY -> {
                var category = categoryService.require(parseId(block.getLinkTarget()));
                if (category.getLevel() == null || category.getLevel() > 2) {
                    throw new ScmBusinessException(INVALID_LINK);
                }
            }
            case ACTIVITY -> {
                try {
                    activityService.detail(parseId(block.getLinkTarget()));
                } catch (ScmBusinessException missingActivity) {
                    throw new ScmBusinessException(INVALID_LINK);
                }
            }
            case EXTERNAL_URL -> {
                if (!allowedExternalUrl(block.getLinkTarget())) {
                    throw new ScmBusinessException(INVALID_LINK);
                }
            }
        }
    }

    public boolean allowedExternalUrl(String target) {
        if (target == null)
            return false;
        try {
            URI uri = URI.create(target);
            Set<String> hosts = Arrays.stream(allowedExternalHosts.split(",")).map(host -> host.trim().toLowerCase())
                    .filter(host -> !host.isEmpty()).collect(Collectors.toSet());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null
                    && uri.getPort() == -1 && hosts.contains(uri.getHost().toLowerCase());
        } catch (IllegalArgumentException invalidUri) {
            return false;
        }
    }

    private static Long parseId(String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value > 0)
                return value;
        } catch (RuntimeException invalidId) {
            // Invalid targets receive the same business error as unknown links.
        }
        throw new ScmBusinessException(INVALID_LINK);
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
