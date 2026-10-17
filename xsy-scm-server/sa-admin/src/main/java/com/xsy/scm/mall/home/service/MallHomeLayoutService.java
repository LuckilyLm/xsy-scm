package com.xsy.scm.mall.home.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.mall.home.dao.MallHomeDao;
import com.xsy.scm.mall.home.constant.MallHomePublishKind;
import com.xsy.scm.mall.home.domain.form.MallHomeBlockForm;
import com.xsy.scm.mall.home.domain.form.MallHomeDraftSaveForm;
import com.xsy.scm.mall.home.domain.form.MallHomePublishForm;
import com.xsy.scm.mall.home.domain.form.MallHomeRollbackForm;
import com.xsy.scm.mall.home.domain.vo.MallHomeDraftVO;
import com.xsy.scm.mall.home.domain.vo.MallHomeReleaseVO;
import com.xsy.scm.mall.home.domain.vo.MallHomeStorageVO;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.mall.home.constant.MallHomeErrorCode.INVALID_LAYOUT;
import static com.xsy.scm.mall.home.constant.MallHomeErrorCode.RELEASE_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class MallHomeLayoutService {
    private static final TypeReference<List<MallHomeBlockForm>> BLOCKS = new TypeReference<>() {
    };
    private final MallHomeDao homeDao;
    private final MallHomeLayoutValidator validator;
    private final ObjectMapper objectMapper;
    private final ScmDataScopeService scopeService;

    @Transactional(readOnly = true)
    public MallHomeDraftVO draft() {
        requireGlobalCustomerScope();
        MallHomeStorageVO draft = homeDao.draft();
        MallHomeStorageVO pointer = homeDao.pointer();
        return new MallHomeDraftVO(draft.getVersion(), pointer.getVersion(), pointer.getCurrentReleaseId(),
                parse(draft.getContent()), draft.getUpdatedAt(), draft.getUpdatedBy());
    }

    @Transactional(rollbackFor = Exception.class)
    public MallHomeDraftVO save(MallHomeDraftSaveForm form) {
        requireGlobalCustomerScope();
        MallHomeStorageVO draft = homeDao.lockDraft();
        if (!Objects.equals(draft.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        validator.validate(form.getBlocks());
        List<MallHomeBlockForm> ordered = form.getBlocks().stream()
                .sorted(Comparator.comparing(MallHomeBlockForm::getSortOrder)).toList();
        if (homeDao.saveDraft(draft.getVersion(), stringify(ordered), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        return draft();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long publish(MallHomePublishForm form) {
        requireGlobalCustomerScope();
        MallHomeStorageVO draft = homeDao.lockDraft();
        MallHomeStorageVO pointer = homeDao.lockPointer();
        if (!Objects.equals(draft.getVersion(), form.getDraftVersion())
                || !Objects.equals(pointer.getVersion(), form.getPointerVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        List<MallHomeBlockForm> blocks = parse(draft.getContent());
        if (blocks.isEmpty()) {
            throw new ScmBusinessException(INVALID_LAYOUT);
        }
        validator.validate(blocks);
        MallHomeReleaseVO release = new MallHomeReleaseVO();
        release.setContent(stringify(blocks));
        release.setPublishKind(MallHomePublishKind.PUBLISH.name());
        release.setPublishedBy(ScmOperator.current());
        homeDao.insertRelease(release);
        activate(pointer, release);
        return release.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long rollback(MallHomeRollbackForm form) {
        requireGlobalCustomerScope();
        MallHomeStorageVO pointer = homeDao.lockPointer();
        if (!Objects.equals(pointer.getVersion(), form.getPointerVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        MallHomeReleaseVO target = homeDao.release(form.getTargetReleaseId());
        if (target == null) {
            throw new ScmBusinessException(RELEASE_NOT_FOUND);
        }
        if (Objects.equals(pointer.getCurrentReleaseId(), target.getId())) {
            throw new ScmBusinessException(INVALID_LAYOUT);
        }
        validator.validate(parse(target.getContent()));
        MallHomeReleaseVO release = new MallHomeReleaseVO();
        release.setContent(target.getContent());
        release.setPublishKind(MallHomePublishKind.ROLLBACK.name());
        release.setSourceReleaseId(target.getId());
        release.setReason(form.getReason().trim());
        release.setPublishedBy(ScmOperator.current());
        homeDao.insertRelease(release);
        activate(pointer, release);
        return release.getId();
    }

    @Transactional(readOnly = true)
    public List<MallHomeReleaseVO> history() {
        requireGlobalCustomerScope();
        return homeDao.history();
    }

    public void requireGlobalCustomerScope() {
        if (!scopeService.resolve().getCustomerSellerScope().isAll()) {
            throw new ScmDataScopeException();
        }
    }

    public List<MallHomeBlockForm> parse(String content) {
        try {
            return objectMapper.readValue(content, BLOCKS);
        } catch (JsonProcessingException invalidStoredLayout) {
            throw new IllegalStateException("Invalid stored mall home layout", invalidStoredLayout);
        }
    }

    private String stringify(List<MallHomeBlockForm> blocks) {
        try {
            return objectMapper.writeValueAsString(blocks);
        } catch (JsonProcessingException invalidLayout) {
            throw new ScmBusinessException(INVALID_LAYOUT);
        }
    }

    private void activate(MallHomeStorageVO pointer, MallHomeReleaseVO release) {
        if (homeDao.activate(pointer.getVersion(), release.getId(), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }
}
