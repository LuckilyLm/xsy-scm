package com.xsy.scm.mall.home.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.home.constant.MallHomeLinkType;
import com.xsy.scm.mall.home.constant.MallHomePopupDisplayRule;
import com.xsy.scm.mall.home.dao.MallHomeDao;
import com.xsy.scm.mall.home.domain.form.MallHomePopupForm;
import com.xsy.scm.mall.home.domain.form.MallHomePopupItemForm;
import com.xsy.scm.mall.home.domain.vo.MallHomePopupItemVO;
import com.xsy.scm.mall.home.domain.vo.MallHomePopupStorageVO;
import com.xsy.scm.mall.home.domain.vo.MallHomePopupVO;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.module.support.file.constant.FileFolderTypeEnum;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.mall.home.constant.MallHomeErrorCode.INVALID_IMAGE;
import static com.xsy.scm.mall.home.constant.MallHomeErrorCode.INVALID_LINK;
import static com.xsy.scm.mall.home.constant.MallHomeErrorCode.INVALID_POPUP;

@Service
@RequiredArgsConstructor
public class MallHomePopupService {
    private static final String PUBLIC_IMAGE_PREFIX = FileFolderTypeEnum.PUBLIC_IMAGE.getFolder();
    private static final TypeReference<List<MallHomePopupItemForm>> ITEMS = new TypeReference<>() {
    };
    private static final int MAX_ITEMS = 5;

    private final MallHomeDao homeDao;
    private final MallHomeLayoutService layoutService;
    private final MallHomeLayoutValidator layoutValidator;
    private final FileService fileService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public MallHomePopupVO detail() {
        layoutService.requireGlobalCustomerScope();
        return view(homeDao.popup());
    }

    @Transactional(rollbackFor = Exception.class)
    public MallHomePopupVO save(MallHomePopupForm form) {
        layoutService.requireGlobalCustomerScope();
        homeDao.ensurePopup();
        MallHomePopupStorageVO storage = homeDao.lockPopup();
        if (!Objects.equals(storage.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        List<MallHomePopupItemForm> items = form.getItems() == null
            ? List.of()
            : form.getItems().stream().filter(Objects::nonNull).toList();
        validate(form, items);
        if (homeDao.savePopup(storage.getVersion(), form.getEnabled(), form.getDisplayRule(), form.getStartAt(),
            form.getEndAt(), stringify(items), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        return view(homeDao.popup());
    }

    /**
     * 客户端读取：只返回启用且在生效窗口内的弹窗；频控（每日一次/每次进入）由客户端按版本记录。
     */
    @Transactional(readOnly = true)
    public MallHomePopupVO current() {
        MallHomePopupStorageVO storage = homeDao.popup();
        MallHomePopupVO view = view(storage);
        OffsetDateTime now = OffsetDateTime.now();
        boolean inWindow = view.getStartAt() == null || !now.isBefore(view.getStartAt());
        inWindow = inWindow && (view.getEndAt() == null || now.isBefore(view.getEndAt()));
        boolean usable = Boolean.TRUE.equals(view.getEnabled()) && inWindow && !view.getItems().isEmpty();
        view.setActive(usable);
        if (!usable) {
            view.setItems(List.of());
        }
        return view;
    }

    private MallHomePopupVO view(MallHomePopupStorageVO storage) {
        MallHomePopupVO view = new MallHomePopupVO();
        if (storage == null) {
            view.setVersion(0);
            view.setEnabled(false);
            view.setDisplayRule(MallHomePopupDisplayRule.DAILY.name());
            view.setItems(List.of());
            view.setActive(false);
            return view;
        }
        view.setVersion(storage.getVersion());
        view.setEnabled(Boolean.TRUE.equals(storage.getEnabled()));
        view.setDisplayRule(storage.getDisplayRule());
        view.setStartAt(storage.getStartAt());
        view.setEndAt(storage.getEndAt());
        view.setUpdatedAt(storage.getUpdatedAt());
        view.setUpdatedBy(storage.getUpdatedBy());
        List<MallHomePopupItemForm> items = parse(storage.getContent());
        Map<String, String> urls = imageUrls(items);
        List<MallHomePopupItemVO> views = new ArrayList<>();
        for (MallHomePopupItemForm item : items) {
            if (item.getImageFileKey() == null || !urls.containsKey(item.getImageFileKey())) {
                continue;
            }
            MallHomePopupItemVO row = new MallHomePopupItemVO();
            row.setImageFileKey(item.getImageFileKey());
            row.setImageUrl(urls.get(item.getImageFileKey()));
            row.setLinkType(item.getLinkType());
            row.setLinkTarget(item.getLinkTarget());
            row.setTitle(item.getTitle());
            views.add(row);
        }
        view.setItems(views);
        return view;
    }

    private void validate(MallHomePopupForm form, List<MallHomePopupItemForm> items) {
        if (items.size() > MAX_ITEMS) {
            throw new ScmBusinessException(INVALID_POPUP);
        }
        if (form.getStartAt() != null && form.getEndAt() != null && !form.getEndAt().isAfter(form.getStartAt())) {
            throw new ScmBusinessException(INVALID_POPUP);
        }
        if (Boolean.TRUE.equals(form.getEnabled()) && items.isEmpty()) {
            throw new ScmBusinessException(INVALID_POPUP);
        }
        List<String> imageKeys = items.stream().map(MallHomePopupItemForm::getImageFileKey)
            .filter(key -> key != null && !key.isBlank()).distinct().toList();
        if (imageKeys.size() != items.size()
            || imageKeys.stream().anyMatch(key -> !key.startsWith(PUBLIC_IMAGE_PREFIX))) {
            throw new ScmBusinessException(INVALID_IMAGE);
        }
        if (!imageKeys.isEmpty() && fileService.getFileList(imageKeys).size() != imageKeys.size()) {
            throw new ScmBusinessException(INVALID_IMAGE);
        }
        for (MallHomePopupItemForm item : items) {
            MallHomeLinkType linkType;
            try {
                linkType = MallHomeLinkType
                    .valueOf(item.getLinkType() == null ? MallHomeLinkType.NONE.name() : item.getLinkType());
            } catch (IllegalArgumentException invalidType) {
                throw new ScmBusinessException(INVALID_LINK);
            }
            if (linkType != MallHomeLinkType.NONE && linkType != MallHomeLinkType.COUPONS
                && linkType != MallHomeLinkType.SEARCH
                && (item.getLinkTarget() == null || item.getLinkTarget().isBlank())) {
                throw new ScmBusinessException(INVALID_LINK);
            }
            if (linkType == MallHomeLinkType.EXTERNAL_URL
                && !layoutValidator.allowedExternalUrl(item.getLinkTarget())) {
                throw new ScmBusinessException(INVALID_LINK);
            }
        }
    }

    private Map<String, String> imageUrls(List<MallHomePopupItemForm> items) {
        List<String> keys = items.stream().map(MallHomePopupItemForm::getImageFileKey)
            .filter(key -> key != null && !key.isBlank()).distinct().toList();
        Map<String, String> urls = new HashMap<>();
        if (keys.isEmpty()) {
            return urls;
        }
        fileService.getFileList(keys).stream().filter(file -> file.getFileUrl() != null && !file.getFileUrl().isBlank())
            .forEach(file -> urls.put(file.getFileKey(), file.getFileUrl()));
        return urls;
    }

    private List<MallHomePopupItemForm> parse(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(content, ITEMS);
        } catch (JsonProcessingException invalidContent) {
            throw new IllegalStateException("Invalid mall home popup content", invalidContent);
        }
    }

    private String stringify(List<MallHomePopupItemForm> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException invalidContent) {
            throw new ScmBusinessException(INVALID_POPUP);
        }
    }
}
