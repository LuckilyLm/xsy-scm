package com.xsy.scm.customer.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.constant.CustomerErrorCode;
import com.xsy.scm.customer.dao.CustomerPromotionDao;
import com.xsy.scm.customer.domain.vo.CustomerPromotionVO;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户专属推广二维码。
 *
 * <p>
 * 码属于客户，业务员取扫码时客户当时的 seller_id：客户换了业务员，历史扫码仍能看出当时是谁的线索。扫码只追加记录，不改归属；真正挂到业务员名下是后台建客户时。
 */
@Service
@RequiredArgsConstructor
public class CustomerPromotionService {

    /** 去掉容易看错的 0/O/1/I，现场抄码不会抄错。 */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int CODE_LENGTH = 10;

    private static final int USER_AGENT_LIMIT = 300;

    private static final int SCAN_PAGE_LIMIT = 100;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomerPromotionDao customerPromotionDao;

    private final CustomerService customerService;

    /**
     * 生成推广码：先撤销旧码再发新的。旧码要当场失效，否则同一客户有多个入口，扫码来源就说不清。
     */
    @Transactional(rollbackFor = Exception.class)
    public CustomerPromotionVO create(Long customerId, String operator) {
        customerService.requireInCurrentScope(customerId);
        customerPromotionDao.revokeActiveByCustomer(customerId, OffsetDateTime.now());

        CustomerPromotionVO row = new CustomerPromotionVO();
        row.setCustomerId(customerId);
        row.setCode(newCode());
        row.setCreatedBy(operator);
        customerPromotionDao.insertCode(row);
        return customerPromotionDao.findById(row.getId());
    }

    @Transactional(readOnly = true)
    public List<CustomerPromotionVO> list(Long customerId) {
        customerService.requireInCurrentScope(customerId);
        return customerPromotionDao.listByCustomer(customerId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id) {
        CustomerPromotionVO row = customerPromotionDao.findById(id);
        if (row == null) {
            throw new ScmBusinessException(CustomerErrorCode.PROMOTION_CODE_NOT_FOUND);
        }
        customerService.requireInCurrentScope(row.getCustomerId());
        customerPromotionDao.revokeById(id, OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<CustomerPromotionVO.Scan> scans(Long codeId, Integer limit) {
        CustomerPromotionVO row = customerPromotionDao.findById(codeId);
        if (row == null) {
            throw new ScmBusinessException(CustomerErrorCode.PROMOTION_CODE_NOT_FOUND);
        }
        customerService.requireInCurrentScope(row.getCustomerId());
        int size = limit == null || limit <= 0 ? 20 : Math.min(limit, SCAN_PAGE_LIMIT);
        return customerPromotionDao.listScans(codeId, size);
    }

    /** 最近一次扫码带来的归属，供新增客户时预填业务员。 */
    @Transactional(readOnly = true)
    public CustomerPromotionVO.Scan latestAttribution() {
        return customerPromotionDao.latestAttribution();
    }

    /**
     * 扫码落地：记录来源并返回归属信息。
     *
     * <p>
     * 不登录也能调用，凭证就是码本身；码撤销后立即不可用。
     */
    @Transactional(rollbackFor = Exception.class)
    public CustomerPromotionVO.Landing landing(String code, String clientIp, String userAgent) {
        CustomerPromotionVO row = customerPromotionDao.findByCode(code == null ? "" : code.trim());
        if (row == null || row.getCustomerName() == null) {
            throw new ScmBusinessException(CustomerErrorCode.PROMOTION_CUSTOMER_NOT_FOUND);
        }
        if (row.getRevokedAt() != null) {
            throw new ScmBusinessException(CustomerErrorCode.PROMOTION_CODE_REVOKED);
        }
        OffsetDateTime now = OffsetDateTime.now();
        customerPromotionDao.insertScan(row.getId(), row.getCustomerId(), row.getSellerId(), clientIp,
            truncate(userAgent));

        CustomerPromotionVO.Landing landing = new CustomerPromotionVO.Landing();
        landing.setCustomerName(row.getCustomerName());
        landing.setSellerName(row.getSellerName());
        landing.setScannedAt(now);
        return landing;
    }

    private String newCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }

    private String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= USER_AGENT_LIMIT ? userAgent : userAgent.substring(0, USER_AGENT_LIMIT);
    }
}
