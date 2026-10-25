import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {
  MALL_THEME_PRESETS,
  isValidMallThemeColor,
  mallThemeBaseColor,
  mallThemePalette,
  normalizeMallThemeColor,
  whiteContrast,
} from '../src/utils/mall-theme.ts';

const source = (relative) => readFileSync(new URL(relative, import.meta.url), 'utf8');

test('theme color validation accepts only #RRGGBB and normalizes casing', () => {
  assert.equal(isValidMallThemeColor('#16A34A'), true);
  assert.equal(isValidMallThemeColor('#16a34a'), true);
  assert.equal(isValidMallThemeColor('#abc'), false);
  assert.equal(isValidMallThemeColor('16A34A'), false);
  assert.equal(isValidMallThemeColor('#GGGGGG'), false);
  assert.equal(isValidMallThemeColor(null), false);
  assert.equal(normalizeMallThemeColor(' #16a34a '), '#16A34A');
});

test('custom color overrides preset; blank custom color falls back to the theme preset', () => {
  assert.equal(mallThemeBaseColor('WARM', null), MALL_THEME_PRESETS.WARM);
  assert.equal(mallThemeBaseColor('UNKNOWN', null), MALL_THEME_PRESETS.FRESH);
  assert.equal(mallThemeBaseColor('WARM', '#123456'), '#123456');
  assert.equal(mallThemeBaseColor('WARM', 'not-a-color'), MALL_THEME_PRESETS.WARM);
});

test('derived palette matches the backend MallThemeColors formula', () => {
  assert.deepEqual(mallThemePalette('#0284C7'), {
    primary: '#0284C7',
    dark: '#026CA3',
    ink: '#015A87',
    light: '#E6F3F9',
    disabled: '#8DC8E6',
  });
});

test('white text contrast uses WCAG luminance and rejects overly light brand colors', () => {
  assert.equal(whiteContrast('#FFFFFF'), 1);
  assert.ok(Math.abs(whiteContrast('#16A34A') - 3.31) < 0.05);
  assert.ok(whiteContrast('#FFE600') < 2.5);
  assert.ok(whiteContrast('#0284C7') >= 2.5);
});

test('decoration console exposes all seven tabs and saves theme color with the draft', () => {
  const layout = source('../src/views/business/scm/mall/mall-home-layout.vue');
  for (const tab of ['首页设置', '首页弹窗', '商城公告', '服务条款', '关于我们', '售后规则', '店铺', '发布记录']) {
    assert.match(layout, new RegExp(`tab="${tab}"`), `缺少${tab}标签页`);
  }
  assert.match(layout, /mallHomeApi\.save\(draftVersion\.value, themeCode\.value, themeColor\.value, blocks\.value\)/);
  assert.match(layout, /contrastWarning/);
  // 发布记录独立成页，不能继续堆在右侧预览列底部
  assert.match(layout, /<a-tab-pane key="history" tab="发布记录">/);
  assert.doesNotMatch(layout, /a-collapse/);
  // 保存、预览、发布用直白动词；预览和发布会先静默保存未保存修改
  assert.doesNotMatch(layout, /保存草稿|发布草稿/);
  assert.match(layout, /prepareSavedChanges/);
  assert.match(layout, /function openPreview/);
});

test('popup settings enforce the five-image limit and https external links', () => {
  const popup = source('../src/views/business/scm/mall/components/mall-popup-settings.vue');
  assert.match(popup, /items\.value\.length >= 5/);
  assert.match(popup, /\^https:\\\/\\\//);
  assert.match(popup, /savePopup\(/);
});

test('page editor uploads to the public image folder and never trusts unsaved drafts as published', () => {
  const page = source('../src/views/business/scm/mall/components/mall-page-settings.vue');
  assert.match(page, /FILE_FOLDER_TYPE_ENUM\.PUBLIC_IMAGE\.value/);
  assert.match(page, /previewOpen/);
  assert.match(page, /publishPage/);
  // 预览必须使用同一次发布的标题，不能拿当前草稿标题拼已发布正文
  assert.match(page, /page\.publishedTitle \|\| page\.title/);
  // 保存期间继续编辑时，不能把新输入覆盖成服务端旧快照，也不能误清未保存标记
  assert.match(page, /const snapshot = \{title: title\.value, content: content\.value\}/);
  assert.match(page, /保存期间的新修改尚未保存/);
});

test('home draft save does not clear dirty when the draft changed during the request', () => {
  const layout = source('../src/views/business/scm/mall/mall-home-layout.vue');
  assert.match(layout, /function draftSignature\(\): string/);
  assert.match(layout, /const signature = draftSignature\(\)/);
  assert.match(layout, /draftSignature\(\) === signature/);
  assert.match(layout, /保存期间的新修改尚未保存/);
});

test('store QR is generated from the saved link, not the edited input', () => {
  const store = source('../src/views/business/scm/mall/components/mall-store-settings.vue');
  assert.match(store, /renderQr\(data\.storeLink\)/);
  assert.match(store, /QRCode\.toDataURL\(savedLink\.value/);
  assert.match(store, /\^https:\\\/\\\//);
});
