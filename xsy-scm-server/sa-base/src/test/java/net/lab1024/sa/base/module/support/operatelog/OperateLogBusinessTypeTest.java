package net.lab1024.sa.base.module.support.operatelog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 8 §10.1：详情接口领域归属识别口径测试。
 *
 * <p>识别结果直接决定要不要再要求领域读取权限，因此键名和 URL 段必须与
 * {@code OperateLogMapper.xml} 的结构化筛选一致；这里的反例用来防止有人把
 * {@code skuId} / {@code parentCustomerId} 之类同值不同义的键误判成领域对象。
 */
@DisplayName("操作日志领域归属识别")
class OperateLogBusinessTypeTest {

    @Test
    @DisplayName("按精确 JSON 键识别商品与客户")
    void recognizesProductAndCustomerByExactJsonKeys() {
        assertThat(OperateLogBusinessType.recognize("[{\"spuId\":12,\"skuId\":34}]", "/api/product/spu/edit"))
                .containsExactly(OperateLogBusinessType.PRODUCT);
        assertThat(OperateLogBusinessType.recognize("[{\"customerId\":9,\"parentCustomerId\":3}]", "/api/scm/customer/edit"))
                .containsExactly(OperateLogBusinessType.CUSTOMER);
        assertThat(OperateLogBusinessType.recognize("[{\"spuId\":12,\"customerId\":9}]", "/api/x"))
                .containsExactly(OperateLogBusinessType.PRODUCT, OperateLogBusinessType.CUSTOMER);
    }

    @Test
    @DisplayName("线路 ID 落在路径变量里，故按 URL 的线路前缀段识别")
    void recognizesDeliveryRouteByUrlSegmentOnly() {
        assertThat(OperateLogBusinessType.recognize("[{\"version\":0}]", "/api/scm/delivery/routes/12/print/orders"))
                .containsExactly(OperateLogBusinessType.DELIVERY_ROUTE);
        assertThat(OperateLogBusinessType.recognize("[{\"longitude\":113.9}]", "/api/scm/delivery/routes/12/stops/7"))
                .containsExactly(OperateLogBusinessType.DELIVERY_ROUTE);
    }

    @Test
    @DisplayName("识别不出领域的通用系统日志返回空集，不被额外权限拦截")
    void returnsEmptyForUnrecognizedSystemLogs() {
        assertThat(OperateLogBusinessType.recognize("[{\"configKey\":\"sys.name\"}]", "/api/config/update")).isEmpty();
        assertThat(OperateLogBusinessType.recognize(null, null)).isEmpty();
        assertThat(OperateLogBusinessType.recognize("", "")).isEmpty();
    }
}
