package com.xsy.scm.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import com.xsy.scm.pricing.dao.PriceBatchAuditDao;
import com.xsy.scm.pricing.domain.form.PriceBatchForm;
import com.xsy.scm.pricing.domain.vo.*;
import com.xsy.scm.pricing.manager.PriceBatchValidator;
import com.xsy.scm.common.exception.ScmBusinessException;

import static com.xsy.scm.pricing.constant.PricingErrorCode.*;

@Service
@RequiredArgsConstructor
public class PriceBatchService {
    private final PriceBatchWriter writer;
    private final PriceBatchAuditService audit;
    private final PriceBatchAuditDao audits;

    public PriceBatchResultVO submit(PriceBatchForm form) {
        if (form.getBatchKey() == null || form.getBatchKey().isBlank() || form.getBatchKey().length() > 100 || form.getRows() == null || form.getRows().isEmpty() || form.getRows().size() > 500)
            throw new ScmBusinessException(PRICE_BATCH_ROW_INVALID);
        if (audits.successCount(form.getBatchKey()) > 0) throw new ScmBusinessException(PRICE_BATCH_KEY_DUPLICATE);
        var failures = PriceBatchValidator.validate(form.getRows());
        if (failures.isEmpty()) {
            try {
                return writer.write(form);
            } catch (PriceBatchWriter.Rejected e) {
                failures = e.failures;
            } catch (DuplicateKeyException e) {
                if (audits.successCount(form.getBatchKey()) > 0)
                    throw new ScmBusinessException(PRICE_BATCH_KEY_DUPLICATE);
                throw e;
            }
        }
        audit.failed(form.getBatchKey(), form.getRows().size(), failures);
        return new PriceBatchResultVO(form.getBatchKey(), false, 0, List.of(), failures);
    }
}
