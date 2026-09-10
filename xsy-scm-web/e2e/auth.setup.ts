import {expect, test as setup} from '@playwright/test';

const authStatePath = 'playwright/.auth/admin.json';

setup('authenticate local acceptance administrator', async ({page}) => {
    const username = process.env.XSY_E2E_USERNAME;
    const password = process.env.XSY_E2E_PASSWORD;

    expect(username, 'Set XSY_E2E_USERNAME to a disposable local acceptance account').toBeTruthy();
    expect(password, 'Set XSY_E2E_PASSWORD to the disposable account password').toBeTruthy();

    await page.goto('/login');
    await page.getByLabel('用户名').fill(username!);
    await page.getByLabel('密码').fill(password!);
    await page.getByRole('button', {name: /登\s*录/}).click();

    await expect(page).toHaveURL(/\/products(?:\?|$)/);
    await expect(page.getByRole('table')).toBeVisible();
    await page.context().storageState({path: authStatePath});
});
