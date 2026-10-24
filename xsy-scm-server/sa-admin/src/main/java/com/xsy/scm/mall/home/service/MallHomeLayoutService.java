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

    /**
     * 受控主题：只改配色，不换版式。
     */
    private static final String DEFAULT_THEME = "FRESH";

    private static final java.util.Set<String> THEMES = java.util.Set.of("FRESH", "WARM", "OCEAN");

    private static final TypeReference<List<MallHomeBlockForm>> BLOCKS = new TypeReference<>() {
    };
    private final MallHomeDao homeDao;
    private final MallHomeLayoutValidator layoutValidator;
    private final ObjectMapper objectMapper;
    private final ScmDataScopeService scopeService;

    @Transactional(readOnly = true)
    public MallHomeDraftVO draft() {
        requireGlobalCustomerScope();
        MallHomeStorageVO draft = homeDao.draft();
        MallHomeStorageVO pointer = homeDao.pointer();
        // 存储行被清理过时按「空草稿 + 无发布版本」展示，页面仍可编辑；保存时会补齐行
        return new MallHomeDraftVO(
            draft == null ? 0 : draft.getVersion(),
            pointer == null ? 0 : pointer.getVersion(),
            pointer == null ? null : pointer.getCurrentReleaseId(),
            draft == null ? DEFAULT_THEME : draft.getThemeCode(),
            draft == null ? List.of() : parse(draft.getContent()),
            draft == null ? null : draft.getUpdatedAt(),
            draft == null ? null : draft.getUpdatedBy());
    }

    /** 草稿与指针是单行表，写操作前补齐，避免历史数据被清理后出现空指针。 */
    private void ensureStorage() {
        homeDao.ensureDraft();
        homeDao.ensurePointer();
    }

    @Transactional(rollbackFor = Exception.class)
    public MallHomeDraftVO save(MallHomeDraftSaveForm form) {
        requireGlobalCustomerScope();
        ensureStorage();
        MallHomeStorageVO draft = homeDao.lockDraft();
        if (!Objects.equals(draft.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        layoutValidator.validate(form.getBlocks());
        List<MallHomeBlockForm> ordered = form.getBlocks().stream()
            .sorted(Comparator.comparing(MallHomeBlockForm::getSortOrder)).toList();
        String themeCode = themeOf(form.getThemeCode());
        if (homeDao.saveDraft(draft.getVersion(), stringify(ordered), themeCode, ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        return draft();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long publish(MallHomePublishForm form) {
        requireGlobalCustomerScope();
        ensureStorage();
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
        layoutValidator.validate(blocks);
        MallHomeReleaseVO release = new MallHomeReleaseVO();
        release.setContent(stringify(blocks));
        // 主题跟着发布版本走：回滚时配色一起回去，不会出现「旧版式配新配色」
        release.setThemeCode(draft.getThemeCode());
        release.setPublishKind(MallHomePublishKind.PUBLISH.name());
        release.setPublishedBy(ScmOperator.current());
        homeDao.insertRelease(release);
        activate(pointer, release);
        return release.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long rollback(MallHomeRollbackForm form) {
        requireGlobalCustomerScope();
        ensureStorage();
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
        layoutValidator.validate(parse(target.getContent()));
        MallHomeReleaseVO release = new MallHomeReleaseVO();
        release.setContent(target.getContent());
        release.setThemeCode(target.getThemeCode());
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

    /**
     * 主题只接受受控取值；不传按默认绿色，避免历史调用方一次性全部失败。
     */
    private String themeOf(String themeCode) {
        if (themeCode == null || themeCode.isBlank()) {
            return DEFAULT_THEME;
        }
        String normalized = themeCode.trim().toUpperCase(java.util.Locale.ROOT);
        if (!THEMES.contains(normalized)) {
            throw new ScmBusinessException(INVALID_LAYOUT);
        }
        return normalized;
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
