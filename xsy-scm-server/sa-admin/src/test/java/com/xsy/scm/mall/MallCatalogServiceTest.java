package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.dao.CustomerFrequentSkuDao;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.catalog.dao.MallCatalogDao;
import com.xsy.scm.mall.catalog.service.MallCatalogService;
import com.xsy.scm.mall.catalog.service.MallStockAvailabilityService;
import com.xsy.scm.mall.order.service.MallWarehouseService;
import com.xsy.scm.pricing.service.PriceResolver;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.junit.jupiter.api.Test;

class MallCatalogServiceTest {
    private final MallCatalogDao catalog = mock(MallCatalogDao.class);
    private final MallCatalogService service = new MallCatalogService(catalog, mock(PriceResolver.class),
            mock(CustomerFrequentSkuDao.class), mock(FileService.class), mock(MallWarehouseService.class),
            mock(MallStockAvailabilityService.class));

    @Test
    void invisibleProductUsesTheSameNotFoundResult() {
        RequestMallCustomer customer = new RequestMallCustomer();
        customer.setCustomerId(8L);
        when(catalog.product(8L, 99L)).thenReturn(null);
        assertThatThrownBy(() -> service.product(customer, 99L)).isInstanceOfSatisfying(ScmBusinessException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                        .isEqualTo(MallErrorCode.CATALOG_PRODUCT_NOT_FOUND));
    }
}
