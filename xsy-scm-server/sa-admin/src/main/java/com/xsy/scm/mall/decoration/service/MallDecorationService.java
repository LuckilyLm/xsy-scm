package com.xsy.scm.mall.decoration.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.decoration.constant.MallPageCode;
import com.xsy.scm.mall.decoration.dao.MallDecorationDao;
import com.xsy.scm.mall.decoration.domain.form.MallPagePublishForm;
import com.xsy.scm.mall.decoration.domain.form.MallPageSaveForm;
import com.xsy.scm.mall.decoration.domain.form.MallStoreSaveForm;
import com.xsy.scm.mall.decoration.domain.vo.MallPageVO;
import com.xsy.scm.mall.decoration.domain.vo.MallPublishedPageVO;
import com.xsy.scm.mall.decoration.domain.vo.MallStoreVO;
import com.xsy.scm.mall.home.service.MallHomeLayoutService;

import java.util.Objects;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.util.SmartHtmlSanitizeUtil;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.mall.home.constant.MallHomeErrorCode.INVALID_PAGE;
import static com.xsy.scm.mall.home.constant.MallHomeErrorCode.INVALID_STORE;

@Service
@RequiredArgsConstructor
public class MallDecorationService {
    private final MallDecorationDao decorationDao;
    private final MallHomeLayoutService layoutService;

    @Transactional(readOnly = true)
    public MallPageVO page(String pageCode) {
        layoutService.requireGlobalCustomerScope();
        return requirePage(pageCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public MallPageVO savePage(MallPageSaveForm form) {
        layoutService.requireGlobalCustomerScope();
        requirePageCode(form.getPageCode());
        MallPageVO page = decorationDao.lockPage(form.getPageCode());
        if (page == null) {
            throw new ScmBusinessException(INVALID_PAGE);
        }
        if (!Objects.equals(page.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        // 富文本必须在服务端清洗：客户端编辑器可以绕过，发布后又会直接渲染到小程序页面。
        // 正文列 NOT NULL，空值按空串处理，避免清空正文时写入 null。
        String content = SmartHtmlSanitizeUtil.clean(form.getContent() == null ? "" : form.getContent());
        if (decorationDao.savePage(form.getPageCode(), page.getVersion(), form.getTitle().trim(), content,
            ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        return requirePage(form.getPageCode());
    }

    @Transactional(rollbackFor = Exception.class)
    public MallPageVO publishPage(MallPagePublishForm form) {
        layoutService.requireGlobalCustomerScope();
        requirePageCode(form.getPageCode());
        MallPageVO page = decorationDao.lockPage(form.getPageCode());
        if (page == null) {
            throw new ScmBusinessException(INVALID_PAGE);
        }
        if (!Objects.equals(page.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        // 与后台编辑器一致：有文字或有图片都算有内容；纯图片公告/资质说明不能卡在发布。
        var document = Jsoup.parse(page.getContent() == null ? "" : page.getContent());
        if (document.text().isBlank() && document.select("img").isEmpty()) {
            throw new ScmBusinessException(INVALID_PAGE);
        }
        if (decorationDao.publishPage(form.getPageCode(), page.getVersion(), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        return requirePage(form.getPageCode());
    }

    /** 客户端读取已发布版本；从未发布的页面按「不存在」处理，避免把后台草稿泄露出去。 */
    @Transactional(readOnly = true)
    public MallPublishedPageVO publishedPage(String pageCode) {
        MallPageCode code = requirePageCode(pageCode);
        MallPageVO page = decorationDao.page(code.name());
        if (page == null || page.getPublishedContent() == null) {
            throw new ScmBusinessException(INVALID_PAGE);
        }
        // 标题必须和正文来自同一次发布，不能用当前草稿标题拼旧正文。
        String publishedTitle = page.getPublishedTitle() == null ? page.getTitle() : page.getPublishedTitle();
        return new MallPublishedPageVO(code.name(), publishedTitle, page.getPublishedContent());
    }

    @Transactional(readOnly = true)
    public MallStoreVO store() {
        layoutService.requireGlobalCustomerScope();
        MallStoreVO store = decorationDao.store();
        return store == null ? emptyStore() : store;
    }

    @Transactional(rollbackFor = Exception.class)
    public MallStoreVO saveStore(MallStoreSaveForm form) {
        layoutService.requireGlobalCustomerScope();
        decorationDao.ensureStore();
        MallStoreVO store = decorationDao.lockStore();
        if (store == null) {
            throw new ScmBusinessException(INVALID_STORE);
        }
        if (!Objects.equals(store.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        String link = form.getStoreLink() == null || form.getStoreLink().isBlank() ? null : form.getStoreLink().trim();
        if (decorationDao.saveStore(store.getVersion(), form.getStoreName().trim(), link, ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        return decorationDao.store();
    }

    private MallPageVO requirePage(String pageCode) {
        MallPageCode code = requirePageCode(pageCode);
        MallPageVO page = decorationDao.page(code.name());
        if (page == null) {
            throw new ScmBusinessException(INVALID_PAGE);
        }
        // 空页面不提示；写过草稿但没发布、或发布后又改过，才提示「有未发布修改」。
        boolean hasDraftContent = page.getContent() != null && !page.getContent().isBlank();
        page.setUnpublishedChanges(hasDraftContent
            && (page.getPublishedVersion() == null || !Objects.equals(page.getVersion(), page.getPublishedVersion())));
        return page;
    }

    private MallPageCode requirePageCode(String pageCode) {
        return MallPageCode.of(pageCode == null ? "" : pageCode)
            .orElseThrow(() -> new ScmBusinessException(INVALID_PAGE));
    }

    private MallStoreVO emptyStore() {
        MallStoreVO store = new MallStoreVO();
        store.setVersion(0);
        store.setStoreName("商城");
        return store;
    }
}
