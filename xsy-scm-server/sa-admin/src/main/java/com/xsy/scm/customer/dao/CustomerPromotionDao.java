package com.xsy.scm.customer.dao;

import com.xsy.scm.customer.domain.vo.CustomerPromotionVO;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CustomerPromotionDao {

    /**
     * 撤销该客户上仍有效的推广码：重新生成后旧码立即失效。
     */
    int revokeActiveByCustomer(@Param("customerId") Long customerId, @Param("revokedAt") OffsetDateTime revokedAt);

    int revokeById(@Param("id") Long id, @Param("revokedAt") OffsetDateTime revokedAt);

    int insertCode(CustomerPromotionVO code);

    CustomerPromotionVO findByCode(@Param("code") String code);

    CustomerPromotionVO findById(@Param("id") Long id);

    List<CustomerPromotionVO> listByCustomer(@Param("customerId") Long customerId);

    int insertScan(@Param("codeId") Long codeId, @Param("customerId") Long customerId, @Param("sellerId") Long sellerId,
        @Param("clientIp") String clientIp, @Param("userAgent") String userAgent);

    List<CustomerPromotionVO.Scan> listScans(@Param("codeId") Long codeId, @Param("limit") int limit);

    /**
     * 最近一次扫码带来的归属，供新增客户时预填业务员。
     */
    CustomerPromotionVO.Scan latestAttribution();
}
