package net.lab1024.sa.admin.module.scm.product.manager;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductImageForm;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSpuAddForm;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

import static net.lab1024.sa.admin.module.scm.product.constant.ProductErrorCode.*;

@Component
public class ProductAggregateValidator {
    public void validateSpu(ProductSpuAddForm form) {
        var skus = form.getSkuList();
        if (skus == null || skus.isEmpty()) throw new ScmBusinessException(SKU_REQUIRED);
        if (skus.stream().filter(s -> Boolean.TRUE.equals(s.getDefaultFlag())).count() != 1)
            throw new ScmBusinessException(DEFAULT_SKU_INVALID);
        Set<String> codes = new HashSet<>(), barcodes = new HashSet<>();
        Set<Map<String, String>> specs = new HashSet<>();
        for (var sku : skus) {
            if (!codes.add(normalizeCode(sku.getSkuCode()))) throw new ScmBusinessException(SKU_CODE_DUPLICATE);
            String barcode = trimToNull(sku.getBarcode());
            if (barcode != null && !barcodes.add(barcode)) throw new ScmBusinessException(SKU_BARCODE_DUPLICATE);
            Map<String, String> normalized = new TreeMap<>();
            if (sku.getSpecValues() != null)
                sku.getSpecValues().forEach((k, v) -> normalized.put(normalizeSpec(k), normalizeSpec(v)));
            if (!specs.add(normalized)) throw new ScmBusinessException(SKU_SPEC_DUPLICATE);
            if (sku.getMarketPrice() == null || sku.getMarketPrice().compareTo(BigDecimal.ZERO) < 0)
                throw new ScmBusinessException(SKU_PRICE_INVALID);
        }
        var images = form.getImages();
        validateImages(images);
    }

    /**
     * 图片集合自身的规则：数量上限、主图至多一张、fileKey 不得重复。
     *
     * <p>单独成一是因为图片中心的批量换绑不经 {@link #validateSpu}，只走
     * {@link ProductImageSyncManager#sync}；口径若留在 SPU 表单里，那条链路就会把两张主图
     * 直接顶到 V49 的 {@code uq_product_image_primary_spu} 上，抛出未捕获的
     * {@code DuplicateKeyException}（HTTP 500）而不是稳定的 IMAGE_INVALID。
     */
    public void validateImages(List<ProductImageForm> images) {
        if (images == null || images.size() > 20 || images.stream().filter(i -> Boolean.TRUE.equals(i.getPrimaryFlag())).count() > 1)
            throw new ScmBusinessException(IMAGE_INVALID);
        Set<String> keys = new HashSet<>();
        for (var img : images) {
            String key = trimToNull(img.getFileKey());
            if (key == null || !keys.add(key)) throw new ScmBusinessException(IMAGE_INVALID);
        }
    }

    public static String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeSpec(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
