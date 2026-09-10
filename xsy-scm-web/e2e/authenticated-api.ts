import type {APIRequestContext} from '@playwright/test';

export async function csrfHeaders(request: APIRequestContext): Promise<Record<string, string>> {
    const state = await request.storageState();
    const token = state.cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
    if (!token) {
        throw new Error('Authenticated Playwright context is missing XSRF-TOKEN');
    }
    return {'X-XSRF-TOKEN': decodeURIComponent(token)};
}

export async function mutationHeaders(
    request: APIRequestContext,
    headers: Record<string, string> = {},
): Promise<Record<string, string>> {
    return {...headers, ...await csrfHeaders(request)};
}
