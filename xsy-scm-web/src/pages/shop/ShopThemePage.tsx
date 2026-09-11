import { ReloadOutlined, SaveOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert, Button, Card, ColorPicker, Divider, InputNumber, Segmented, Select, Skeleton, Space, Tag, message,
} from 'antd';
import { useEffect, useState } from 'react';
import { fetchThemeConfig, saveThemeConfig } from '../../api/marketing';
import { PageContainer } from '../../components/common/PageContainer';
import type { MallCardStyle, MallNavigationStyle, MallProductCardStyle, MallThemeConfig } from '../../types/mall';
import styles from './Shop.module.css';

/** 与后端 MallCatalogService.DEFAULT_THEME 保持一致，作为加载失败时的兜底。 */
const FALLBACK_THEME: MallThemeConfig = {
  themeCode: 'FRESH_GREEN',
  primaryColor: '#16a34a',
  accentColor: '#f97316',
  pageBackground: '#f5f7fa',
  cardRadius: 16,
  cardStyle: 'SHADOW',
  productCardStyle: 'COMFORTABLE',
  navigationStyle: 'BOTTOM',
};

const PRESETS: Array<{ code: string; label: string; theme: MallThemeConfig }> = [
  { code: 'FRESH_GREEN', label: '清新绿（默认）', theme: FALLBACK_THEME },
  {
    code: 'WARM_ORANGE',
    label: '暖阳橙',
    theme: { themeCode: 'WARM_ORANGE', primaryColor: '#ea580c', accentColor: '#facc15', pageBackground: '#fff7ed', cardRadius: 12, cardStyle: 'SHADOW', productCardStyle: 'COMFORTABLE', navigationStyle: 'BOTTOM' },
  },
  {
    code: 'OCEAN_BLUE',
    label: '深海蓝',
    theme: { themeCode: 'OCEAN_BLUE', primaryColor: '#2563eb', accentColor: '#06b6d4', pageBackground: '#f1f5f9', cardRadius: 8, cardStyle: 'BORDER', productCardStyle: 'COMPACT', navigationStyle: 'TOP' },
  },
  {
    code: 'MINIMAL_DARK',
    label: '极简墨绿',
    theme: { themeCode: 'MINIMAL_DARK', primaryColor: '#0f766e', accentColor: '#f59e0b', pageBackground: '#f8fafc', cardRadius: 4, cardStyle: 'FLAT', productCardStyle: 'SPACIOUS', navigationStyle: 'BOTTOM' },
  },
];

const cardStyleOptions: Array<{ label: string; value: MallCardStyle }> = [
  { label: '无边框', value: 'FLAT' },
  { label: '投影', value: 'SHADOW' },
  { label: '描边', value: 'BORDER' },
];

const productCardOptions: Array<{ label: string; value: MallProductCardStyle }> = [
  { label: '紧凑', value: 'COMPACT' },
  { label: '舒适', value: 'COMFORTABLE' },
  { label: '宽松', value: 'SPACIOUS' },
];

const navigationOptions: Array<{ label: string; value: MallNavigationStyle }> = [
  { label: '底部标签', value: 'BOTTOM' },
  { label: '顶部标签', value: 'TOP' },
  { label: '侧边抽屉', value: 'SIDEBAR' },
];

/** 主题保存后由 Web 商城预览与小程序共同消费，字段与后端 ThemeConfigSaveRequest 一一对应。 */
export function ShopThemePage() {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<MallThemeConfig>(FALLBACK_THEME);
  const theme = useQuery({ queryKey: ['marketing-theme'], queryFn: fetchThemeConfig });

  useEffect(() => {
    if (theme.data) {
      setDraft(theme.data);
    }
  }, [theme.data]);

  const save = useMutation({
    mutationFn: (payload: MallThemeConfig) => saveThemeConfig(payload),
    onSuccess: async (saved) => {
      setDraft(saved);
      message.success('主题已保存，Web 商城预览与小程序将同步生效');
      await queryClient.invalidateQueries({ queryKey: ['marketing-theme'] });
      await queryClient.invalidateQueries({ queryKey: ['mall-home-preview'] });
    },
    onError: () => message.error('主题保存失败，请稍后重试'),
  });

  const patch = (changes: Partial<MallThemeConfig>) => setDraft((current) => ({ ...current, ...changes }));

  const dirty = Boolean(theme.data) && JSON.stringify(theme.data) !== JSON.stringify(draft);

  if (theme.isError) {
    return (
      <PageContainer title="商城主题">
        <Alert
          type="error"
          showIcon
          message="主题配置加载失败"
          action={<Button onClick={() => theme.refetch()}>重试</Button>}
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer title="商城主题">
      <div className={styles.themeLayout}>
        <Card className={styles.themeForm} title="主题参数" loading={theme.isPending}>
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <div className={styles.field}>
              <span className={styles.fieldLabel}>预设主题</span>
              <Select
                style={{ width: '100%' }}
                value={PRESETS.some((preset) => preset.code === draft.themeCode) ? draft.themeCode : undefined}
                placeholder={draft.themeCode}
                options={PRESETS.map((preset) => ({ label: preset.label, value: preset.code }))}
                onChange={(code) => {
                  const preset = PRESETS.find((item) => item.code === code);
                  if (preset) {
                    setDraft({ ...preset.theme });
                  }
                }}
              />
            </div>

            <div className={styles.field}>
              <span className={styles.fieldLabel}>主题编码</span>
              <Select
                mode="tags"
                maxCount={1}
                style={{ width: '100%' }}
                value={draft.themeCode ? [draft.themeCode] : []}
                onChange={(values: string[]) => patch({ themeCode: values[values.length - 1] ?? '' })}
                options={PRESETS.map((preset) => ({ label: preset.label, value: preset.code }))}
              />
            </div>

            <Divider style={{ margin: 0 }}>配色</Divider>

            <div className={styles.field}>
              <span className={styles.fieldLabel}>主色</span>
              <ColorPicker
                showText
                value={draft.primaryColor}
                onChange={(color) => patch({ primaryColor: color.toHexString().toUpperCase() })}
              />
            </div>
            <div className={styles.field}>
              <span className={styles.fieldLabel}>强调色</span>
              <ColorPicker
                showText
                value={draft.accentColor}
                onChange={(color) => patch({ accentColor: color.toHexString().toUpperCase() })}
              />
            </div>
            <div className={styles.field}>
              <span className={styles.fieldLabel}>页面背景</span>
              <ColorPicker
                showText
                value={draft.pageBackground}
                onChange={(color) => patch({ pageBackground: color.toHexString().toUpperCase() })}
              />
            </div>

            <Divider style={{ margin: 0 }}>卡片与导航</Divider>

            <div className={styles.field}>
              <span className={styles.fieldLabel}>卡片圆角</span>
              <Space>
                <InputNumber
                  min={0}
                  max={32}
                  value={draft.cardRadius}
                  onChange={(value) => patch({ cardRadius: value ?? 0 })}
                />
                <span className={styles.hint}>px（0–32）</span>
              </Space>
            </div>
            <div className={styles.field}>
              <span className={styles.fieldLabel}>卡片样式</span>
              <Segmented
                value={draft.cardStyle}
                options={cardStyleOptions}
                onChange={(value) => patch({ cardStyle: value as MallCardStyle })}
              />
            </div>
            <div className={styles.field}>
              <span className={styles.fieldLabel}>商品卡片</span>
              <Segmented
                value={draft.productCardStyle}
                options={productCardOptions}
                onChange={(value) => patch({ productCardStyle: value as MallProductCardStyle })}
              />
            </div>
            <div className={styles.field}>
              <span className={styles.fieldLabel}>导航样式</span>
              <Segmented
                value={draft.navigationStyle}
                options={navigationOptions}
                onChange={(value) => patch({ navigationStyle: value as MallNavigationStyle })}
              />
            </div>

            <Space>
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={save.isPending}
                disabled={!dirty}
                onClick={() => save.mutate(draft)}
              >
                保存主题
              </Button>
              <Button
                icon={<ReloadOutlined />}
                disabled={!dirty || save.isPending}
                onClick={() => setDraft(theme.data ?? FALLBACK_THEME)}
              >
                放弃修改
              </Button>
              {dirty ? <Tag color="orange">有未保存的修改</Tag> : <Tag color="green">已与线上一致</Tag>}
            </Space>
          </Space>
        </Card>

        <div className={styles.previewPanel}>
          <div className={styles.previewTitle}>
            小程序实时预览
            <span className={styles.hint}>与 Web 商城 / 小程序共用同一份配置</span>
          </div>
          {theme.isPending ? <Skeleton active paragraph={{ rows: 10 }} /> : <PhonePreview theme={draft} />}
        </div>
      </div>
    </PageContainer>
  );
}

function PhonePreview({ theme }: { theme: MallThemeConfig }) {
  const cardShadow = theme.cardStyle === 'FLAT' ? 'none' : '0 6px 18px rgba(31, 35, 41, 0.08)';
  const cardBorder = theme.cardStyle === 'BORDER' ? '1px solid #e5e6eb' : '1px solid transparent';
  const columns = theme.productCardStyle === 'COMPACT' ? 3 : theme.productCardStyle === 'COMFORTABLE' ? 2 : 1;

  return (
    <div className={styles.phone}>
      <div className={styles.phoneScreen} style={{ background: theme.pageBackground }}>
        {theme.navigationStyle === 'TOP' ? (
          <div className={styles.previewTopNav} style={{ background: theme.primaryColor }}>
            <span>首页</span>
            <span style={{ opacity: 0.7 }}>分类</span>
            <span style={{ opacity: 0.7 }}>购物车</span>
            <span style={{ opacity: 0.7 }}>我的</span>
          </div>
        ) : null}

        <div className={styles.previewHeader} style={{ background: theme.primaryColor }}>
          <strong>鲜蔬源商城</strong>
          <span style={{ opacity: 0.8 }}>搜索商品</span>
        </div>

        <div
          className={styles.previewBanner}
          style={{
            background: `linear-gradient(120deg, ${theme.primaryColor}, ${theme.accentColor})`,
            borderRadius: Math.min(theme.cardRadius, 20),
          }}
        >
          <span className={styles.previewBannerTitle}>今日新鲜直达</span>
          <span className={styles.previewBannerText}>限时活动 · 满减优惠</span>
        </div>

        <div className={styles.previewChips}>
          {['叶菜', '根茎', '菌菇', '水果'].map((name) => (
            <span
              key={name}
              className={styles.previewChip}
              style={{ borderRadius: Math.min(theme.cardRadius, 14), color: theme.primaryColor, borderColor: theme.primaryColor }}
            >
              {name}
            </span>
          ))}
        </div>

        <div className={styles.previewGrid} style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}>
          {['有机小白菜 500g', '沙地土豆 1kg', '新鲜香菇 300g', '当季蜜橘 2kg'].map((name, index) => (
            <div
              key={name}
              className={styles.previewProduct}
              style={{ borderRadius: theme.cardRadius, boxShadow: cardShadow, border: cardBorder }}
            >
              <div className={styles.previewProductImage} style={{ background: `${theme.primaryColor}14` }} />
              <div className={styles.previewProductName}>{name}</div>
              <div className={styles.previewProductPrice} style={{ color: theme.accentColor }}>
                ¥{(12.8 + index * 3.4).toFixed(2)}
              </div>
            </div>
          ))}
        </div>

        {theme.navigationStyle === 'BOTTOM' ? (
          <div className={styles.previewBottomNav}>
            <span style={{ color: theme.primaryColor }}>首页</span>
            <span>分类</span>
            <span>购物车</span>
            <span>我的</span>
          </div>
        ) : null}
      </div>
    </div>
  );
}
