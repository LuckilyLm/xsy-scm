package com.xianshuyuan.scm.supplier.converter;

import com.xianshuyuan.scm.supplier.entity.SupplierEntity;
import com.xianshuyuan.scm.supplier.entity.SupplierSkuEntity;
import com.xianshuyuan.scm.supplier.entity.WarehouseEntity;
import com.xianshuyuan.scm.supplier.vo.SupplierSkuVO;
import com.xianshuyuan.scm.supplier.vo.SupplierVO;
import com.xianshuyuan.scm.supplier.vo.WarehouseVO;

import java.math.BigDecimal;
import java.util.Map;

public final class SupplierConverter {
    private SupplierConverter() {
    }

    public static SupplierVO toVO(SupplierEntity entity) {
        return new SupplierVO(
                entity.getId(),
                entity.getSupplierCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getVersion(),
                entity.getRemark()
        );
    }

    public static WarehouseVO toVO(WarehouseEntity entity) {
        return new WarehouseVO(
                entity.getId(),
                entity.getWarehouseCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getVersion(),
                entity.getAddress(),
                entity.getRemark()
        );
    }

    public static SupplierSkuVO toVO(SupplierSkuEntity entity) {
        Map<String, String> specs = entity.getSpecValuesSnapshot();
        return new SupplierSkuVO(
                entity.getId(),
                entity.getSupplierId(),
                entity.getSkuId(),
                entity.getSupplierCodeSnapshot(),
                entity.getSupplierNameSnapshot(),
                entity.getSkuCodeSnapshot(),
                entity.getSkuNameSnapshot(),
                specs == null ? Map.of() : Map.copyOf(specs),
                entity.getPurchaseUnit(),
                decimal(entity.getReferencePrice()),
                entity.getPurchaserId(),
                entity.getDefaultSupplier(),
                entity.getStatus(),
                entity.getVersion()
        );
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.setScale(4).toPlainString();
    }
}
