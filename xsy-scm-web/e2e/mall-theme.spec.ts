import {test, expect} from './scm-test-base';

// H5 浏览器契约使用受控主题响应；真实发布/回滚事实由 MallHomeLayoutPgIT 验证。
const mallUrl = process.env.SCM_MALL_H5_URL;
test('商城主题跨页、刷新、直达及发布版本切换', async ({page}) => {
    test.skip(!mallUrl, '需要独立启动商城 H5 并设置 SCM_MALL_H5_URL');
    let themeCode = 'WARM';
    let releaseId = 1;
    let offline = false;
    await page.route('**/scm/mall/home/theme*', async route => {
        if (offline) return route.abort();
        await route.fulfill({json: {code: 0, ok: true, data: {themeCode, releaseId}}});
    });
    const currentColor = () => page.locator('[style*="--mall-primary:"]').first().evaluate(
        node => getComputedStyle(node).getPropertyValue('--mall-primary').trim());
    await page.goto(`${mallUrl}/#/pages/login/login`);
    await expect.poll(currentColor).toBe('#f97316');
    await page.goto(`${mallUrl}/#/pages/account/password`);
    await expect.poll(currentColor).toBe('#f97316');
    await page.goto(`${mallUrl}/#/pages-sub/mall/web`);
    await expect.poll(currentColor).toBe('#f97316');
    await page.reload();
    await expect.poll(currentColor).toBe('#f97316');
    themeCode = 'OCEAN';
    releaseId = 2;
    await page.goto(`${mallUrl}/#/pages/login/login`);
    await page.reload();
    await expect.poll(currentColor).toBe('#0284c7');
    themeCode = 'WARM';
    releaseId = 3;
    await page.reload();
    await expect.poll(currentColor).toBe('#f97316');
    themeCode = 'UNKNOWN';
    await page.reload();
    await expect.poll(currentColor).toBe('#16a34a');
    offline = true;
    await page.reload();
    await expect.poll(currentColor).toBe('#16a34a');
});
