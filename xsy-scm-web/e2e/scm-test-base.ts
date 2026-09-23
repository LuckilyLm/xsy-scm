/*
  统一的浏览器异常口径：用例只要声明了 page，就自动断言「0 未捕获异常 / 0 未处理 promise rejection」。

  为什么覆盖 page fixture 而不是各用例自己挂监听：挂监听这件事在用例里重复十几遍后，
  漏挂与写法漂移是迟早的事，而「有没有 pageerror」属于验收口径、不属于业务断言。
  未处理 rejection 不会被 pageerror 自动捕获，因此用 init script 在页面脚本之前把
  它转成抛出，让它进入同一条未捕获异常通道；转换发生在导航前，不影响业务代码执行。

  纯接口用例不声明 page，本 fixture 不会实例化，也不会为它们启动浏览器。
*/
import {expect, test as base, type Page} from '@playwright/test';

export const test = base.extend<{page: Page}>({
    page: async ({page}, use) => {
        const errors: string[] = [];
        page.on('pageerror', (e) => errors.push(e.message));
        await page.addInitScript(() => {
            window.addEventListener('unhandledrejection', (event) => {
                throw event.reason instanceof Error ? event.reason : new Error(`unhandled promise rejection: ${String(event.reason)}`);
            });
        });
        await use(page);
        expect(errors, `浏览器未捕获异常：${errors.join(' | ')}`).toEqual([]);
    },
});

export {expect};
