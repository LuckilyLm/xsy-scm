package net.lab1024.sa.base.common.util;

import cn.hutool.core.util.StrUtil;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/**
 * 富文本 HTML 白名单清洗。
 *
 * <p><b>为什么必须在服务端做</b>：公告与帮助文档的正文以 HTML 存储，前端用 {@code v-html} 直接注入 DOM。
 * 只在前端过滤是无效的 —— 攻击者绕过页面直接调接口提交即可，而这篇正文会被所有有权限查看的人加载执行。
 * 后台的富文本编辑器本质上是一个「可跨用户投放 HTML」的入口，必须按不可信输入对待。
 *
 * <p><b>策略</b>：白名单，不是黑名单。只保留排版必需的标签与属性，其余一律剥掉；
 * 标签被移除时保留其文本内容（{@code Safelist} 的默认行为），避免把正文吃掉。
 * 关键的三类：
 * <ul>
 * <li>脚本与可执行内容：{@code script} / {@code iframe} / {@code object} / {@code embed} / {@code form} 等不在白名单内，直接移除；</li>
 * <li>事件属性：{@code on*} （{@code onclick}、{@code onerror}…）不在允许属性内，直接移除；</li>
 * <li>危险协议：{@code javascript:} / {@code vbscript:} / {@code data:} 等，由协议白名单拦下。</li>
 * </ul>
 */
public class SmartHtmlSanitizeUtil {

    /**
     * 允许的标签与属性集合。
     *
     * <p>基于 jsoup 的 {@code basicWithImages} 收紧后放开常用排版标签：
     * 表格、代码块、引用、标题、列表都保留（后台正文的实际用法），
     * 但<b>不</b>放开 {@code style} 属性与 {@code iframe} —— 前者可用于视觉欺骗与外部资源探测，后者是另一个页面。
     */
    private static final Safelist SAFELIST = Safelist.basicWithImages()
            .addTags("h1", "h2", "h3", "h4", "h5", "h6", "table", "thead", "tbody", "tr", "th", "td",
                    "hr", "pre", "code", "s", "u", "sub", "sup", "span", "div")
            .addAttributes("table", "border", "cellpadding", "cellspacing", "width")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("th", "colspan", "rowspan")
            .addAttributes("img", "width", "height", "alt", "title");

    /** 允许的链接协议。相对地址、锚点、邮件没有协议，由 jsoup 单独放行。 */
    private static final Set<String> ALLOWED_PROTOCOLS = Set.of("http", "https", "mailto");

    /**
     * 清洗时必须给出的基准地址。
     *
     * <p>不是用来改写的 —— 它只是让 jsoup 能把 {@code /admin/notice}、{@code #anchor} 这类
     * <b>相对地址</b>解析成合法 URL。baseUri 给空串时相对地址解析失败，
     * 会被当成非法协议整条摘掉，正文里的站内链接与图片就会消失。
     * 用固定的占位域名，不用真实域名：清洗结果里不会出现它（jsoup 只做校验，不回写）。
     */
    private static final String SANITIZE_BASE_URI = "https://sanitize.invalid/";

    /** 协议黑名单，用于在放行前做一次显式拦截（含 jsoup 归一化后仍可能出现的写法）。 */
    private static final Pattern DANGEROUS_PROTOCOL = Pattern.compile(
            "^\\s*(javascript|vbscript|data|file|blob)\\s*:", Pattern.CASE_INSENSITIVE);

    private SmartHtmlSanitizeUtil() {
    }

    /**
     * 清洗富文本 HTML。
     *
     * @param html 原始 HTML，可为空
     * @return 清洗后的 HTML；入参为空时返回原值（{@code null} 或空串），不制造空字符串
     */
    public static String clean(String html) {
        if (StrUtil.isBlank(html)) {
            return html;
        }
        // 先做一次显式协议拦截：jsoup 的 Safelist 已能处理绝大多数情形，
        // 但「javascript:」写在 href 里时各浏览器解析差异较大，这里提前掐掉更稳妥。
        String guarded = stripDangerousProtocols(html);
        // OutputSettings 关掉 prettyPrint 不只是为了好看：开启时 jsoup 会重排版并可能改变正文空白，
        // 例如把 <pre> 里的缩进压掉 —— 那属于内容损坏，不是清洗。
        Document.OutputSettings settings = new Document.OutputSettings().prettyPrint(false);
        return Jsoup.clean(guarded, SANITIZE_BASE_URI, SAFELIST, settings);
    }

    /**
     * 把 {@code href/src} 上形如 {@code javascript:...} 的值摘掉，保留标签本身。
     *
     * <p>之所以不在这里做正则替换字符串：正则改 HTML 很容易改坏；
     * 交给 jsoup 解析成 DOM，逐个属性判断，改完再序列化回去。
     */
    private static String stripDangerousProtocols(String html) {
        Document document = Jsoup.parseBodyFragment(html);
        for (Element element : document.getAllElements()) {
            stripAttribute(element, "href");
            stripAttribute(element, "src");
            stripAttribute(element, "srcset");
            stripAttribute(element, "action");
            stripAttribute(element, "formaction");
            stripAttribute(element, "xlink:href");
        }
        document.outputSettings().prettyPrint(false);
        return document.body().html();
    }

    private static void stripAttribute(Element element, String attribute) {
        String value = element.attr(attribute);
        if (StrUtil.isBlank(value)) {
            return;
        }
        if (DANGEROUS_PROTOCOL.matcher(value).find()) {
            element.removeAttr(attribute);
            return;
        }
        // 有协议但不在白名单（例如 ftp:、某自定义 scheme）同样摘掉；无协议（相对路径、#锚点）放行。
        String scheme = schemeOf(value);
        if (scheme != null && !ALLOWED_PROTOCOLS.contains(scheme)) {
            element.removeAttr(attribute);
        }
    }

    /** 取协议名，没有协议返回 {@code null}。注意不能简单按 {@code :} 切分：路径里也可能有冒号。 */
    private static String schemeOf(String value) {
        int colon = value.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        if (value.indexOf('/') >= 0 && value.indexOf('/') < colon) {
            return null;
        }
        if (value.indexOf('#') >= 0 && value.indexOf('#') < colon) {
            return null;
        }
        return value.substring(0, colon).trim().toLowerCase();
    }
}
