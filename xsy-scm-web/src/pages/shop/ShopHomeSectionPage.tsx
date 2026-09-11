import { ArrowDownOutlined, ArrowUpOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert, Button, Card, DatePicker, Form, Input, InputNumber, Modal, Select, Space, Switch, Table, Tag, message,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useMemo, useState } from 'react';
import { createHomeSection, fetchHomeSections, updateHomeSection } from '../../api/marketing';
import { PageContainer } from '../../components/common/PageContainer';
import {
  customSectionKindLabels,
  homeSectionTypeLabels,
  type CustomSectionKind,
  type HomeSection,
  type HomeSectionSaveRequest,
  type HomeSectionType,
} from '../../types/marketing';
import styles from './Shop.module.css';

const sectionTypeOptions = (Object.keys(homeSectionTypeLabels) as HomeSectionType[]).map((value) => ({
  value,
  label: homeSectionTypeLabels[value],
}));

const kindOptions = (Object.keys(customSectionKindLabels) as CustomSectionKind[]).map((value) => ({
  value,
  label: customSectionKindLabels[value],
}));

interface SectionFormValues {
  sectionType: HomeSectionType;
  title?: string;
  sortOrder?: number;
  status: 'ENABLED' | 'DISABLED';
  promotionId?: number;
  categoryId?: number;
  range?: [Dayjs | null, Dayjs | null];
  kind?: CustomSectionKind;
  content?: string;
  linkUrl?: string;
  imageUrl?: string;
  alwaysShow?: boolean;
}

/** 板块类型标签：CUSTOM 进一步区分公告 / 弹窗。 */
function SectionTypeTag({ section }: { section: HomeSection }) {
  if (section.sectionType === 'CUSTOM') {
    const kind = section.payload?.kind;
    return (
      <Tag className={styles.sectionKindTag} color={kind === 'POPUP' ? 'purple' : 'blue'}>
        {kind ? customSectionKindLabels[kind] : '自定义板块'}
      </Tag>
    );
  }
  return <Tag color="cyan">{homeSectionTypeLabels[section.sectionType]}</Tag>;
}

function summarize(section: HomeSection): string {
  if (section.sectionType === 'CUSTOM') {
    return section.payload?.content?.slice(0, 40) || '—';
  }
  if (section.sectionType === 'CATEGORY') {
    return section.categoryId ? `分类 #${section.categoryId}` : '全部一级分类';
  }
  if (section.promotionId) {
    return `促销 #${section.promotionId}`;
  }
  return section.payload?.imageUrl ? '已配置图片' : '默认展示';
}

export function ShopHomeSectionPage() {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<HomeSection | null>(null);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<SectionFormValues>();
  const sections = useQuery({ queryKey: ['marketing-home-sections'], queryFn: fetchHomeSections });

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ['marketing-home-sections'] });
    await queryClient.invalidateQueries({ queryKey: ['mall-home-preview'] });
  };

  const persist = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: HomeSectionSaveRequest }) =>
      updateHomeSection(id, payload),
    onSuccess: async () => {
      message.success('已保存，Web 商城与小程序同步生效');
      await invalidate();
    },
    onError: () => message.error('保存失败，请稍后重试'),
  });

  const create = useMutation({
    mutationFn: (payload: HomeSectionSaveRequest) => createHomeSection(payload),
    onSuccess: async () => {
      message.success('板块已创建');
      setOpen(false);
      setEditing(null);
      form.resetFields();
      await invalidate();
    },
    onError: () => message.error('创建失败，请检查表单后重试'),
  });

  const rows = useMemo(
    () => [...(sections.data ?? [])].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id),
    [sections.data],
  );

  const toSaveRequest = (section: HomeSection, changes: Partial<HomeSectionSaveRequest>): HomeSectionSaveRequest => ({
    sectionType: section.sectionType,
    title: section.title,
    promotionId: section.promotionId,
    categoryId: section.categoryId,
    sortOrder: section.sortOrder,
    status: section.status,
    startAt: section.startAt,
    endAt: section.endAt,
    payload: section.payload,
    ...changes,
  });

  /** 上移 / 下移通过交换相邻板块的 sortOrder 实现，避免引入额外的排序接口。 */
  const move = async (section: HomeSection, direction: -1 | 1) => {
    const index = rows.findIndex((item) => item.id === section.id);
    const target = rows[index + direction];
    if (!target) {
      return;
    }
    try {
      await updateHomeSection(section.id, toSaveRequest(section, { sortOrder: target.sortOrder }));
      await updateHomeSection(target.id, toSaveRequest(target, { sortOrder: section.sortOrder }));
      message.success('排序已更新');
      await invalidate();
    } catch {
      message.error('排序失败，请刷新后重试');
      await invalidate();
    }
  };

  const openCreate = (sectionType: HomeSectionType, kind?: CustomSectionKind) => {
    setEditing(null);
    form.setFieldsValue({
      sectionType,
      status: 'ENABLED',
      sortOrder: (rows.at(-1)?.sortOrder ?? 0) + 10,
      kind,
      alwaysShow: false,
    });
    setOpen(true);
  };

  const openEdit = (section: HomeSection) => {
    setEditing(section);
    form.setFieldsValue({
      sectionType: section.sectionType,
      title: section.title ?? undefined,
      sortOrder: section.sortOrder,
      status: section.status,
      promotionId: section.promotionId ?? undefined,
      categoryId: section.categoryId ?? undefined,
      range: section.startAt || section.endAt
        ? [section.startAt ? dayjs(section.startAt) : null, section.endAt ? dayjs(section.endAt) : null]
        : undefined,
      kind: section.payload?.kind,
      content: section.payload?.content,
      linkUrl: section.payload?.linkUrl,
      imageUrl: section.payload?.imageUrl,
      alwaysShow: section.payload?.alwaysShow ?? false,
    });
    setOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const payload: HomeSectionSaveRequest = {
      sectionType: values.sectionType,
      title: values.title?.trim() || null,
      sortOrder: values.sortOrder ?? 0,
      status: values.status,
      promotionId: values.promotionId ?? null,
      categoryId: values.categoryId ?? null,
      startAt: values.range?.[0]?.toISOString() ?? null,
      endAt: values.range?.[1]?.toISOString() ?? null,
      payload: values.sectionType === 'CUSTOM'
        ? {
            kind: values.kind ?? 'NOTICE',
            content: values.content?.trim() ?? '',
            linkUrl: values.linkUrl?.trim() || undefined,
            imageUrl: values.imageUrl?.trim() || undefined,
            alwaysShow: values.alwaysShow ?? false,
          }
        : values.imageUrl?.trim()
          ? { imageUrl: values.imageUrl.trim() }
          : null,
    };
    if (editing) {
      await persist.mutateAsync({ id: editing.id, payload });
      setOpen(false);
      setEditing(null);
      form.resetFields();
      return;
    }
    await create.mutateAsync(payload);
  };

  const watchedType = Form.useWatch('sectionType', form);

  return (
    <PageContainer title="首页装修">
      <div className={styles.sectionToolbar}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate('BANNER')}>
          新建板块
        </Button>
        <Button onClick={() => openCreate('CUSTOM', 'NOTICE')}>新增公告通知</Button>
        <Button onClick={() => openCreate('CUSTOM', 'POPUP')}>新增启动弹窗</Button>
        <Button icon={<ReloadOutlined />} onClick={() => void sections.refetch()}>
          刷新
        </Button>
      </div>

      {sections.isError ? (
        <Alert
          style={{ marginBottom: 12 }}
          type="error"
          showIcon
          message="首页板块加载失败"
          action={<Button onClick={() => sections.refetch()}>重试</Button>}
        />
      ) : null}

      <Card bodyStyle={{ padding: 0 }}>
        <Table
          rowKey="id"
          loading={sections.isPending}
          dataSource={rows}
          pagination={false}
          locale={{ emptyText: '暂无板块，先新建一个运营横幅吧' }}
          columns={[
            {
              title: '板块',
              dataIndex: 'sectionType',
              width: 150,
              render: (_: unknown, row: HomeSection) => <SectionTypeTag section={row} />,
            },
            {
              title: '标题 / 内容',
              width: 280,
              render: (_: unknown, row: HomeSection) => (
                <div>
                  <div>{row.title || <span className={styles.hint}>未设置标题</span>}</div>
                  <div className={styles.hint}>{summarize(row)}</div>
                </div>
              ),
            },
            {
              title: '排序',
              dataIndex: 'sortOrder',
              width: 130,
              render: (value: number, row: HomeSection) => (
                <InputNumber
                  min={0}
                  value={value}
                  onChange={(next) => {
                    if (next === null || next === value) {
                      return;
                    }
                    persist.mutate({ id: row.id, payload: toSaveRequest(row, { sortOrder: next }) });
                  }}
                />
              ),
            },
            {
              title: '启用',
              dataIndex: 'status',
              width: 90,
              align: 'center',
              render: (value: string, row: HomeSection) => (
                <Switch
                  checked={value === 'ENABLED'}
                  loading={persist.isPending && persist.variables?.id === row.id}
                  onChange={(checked) =>
                    persist.mutate({
                      id: row.id,
                      payload: toSaveRequest(row, { status: checked ? 'ENABLED' : 'DISABLED' }),
                    })
                  }
                />
              ),
            },
            {
              title: '生效时间',
              width: 200,
              render: (_: unknown, row: HomeSection) => (
                <span className={styles.hint}>
                  {row.startAt || row.endAt
                    ? `${row.startAt ? dayjs(row.startAt).format('MM-DD HH:mm') : '不限'} ~ ${
                        row.endAt ? dayjs(row.endAt).format('MM-DD HH:mm') : '不限'
                      }`
                    : '长期有效'}
                </span>
              ),
            },
            {
              title: '操作',
              width: 180,
              fixed: 'right',
              render: (_: unknown, row: HomeSection) => [
                <Button key="edit" type="link" onClick={() => openEdit(row)}>
                  编辑
                </Button>,
                <Button
                  key="up"
                  type="link"
                  icon={<ArrowUpOutlined />}
                  disabled={rows.findIndex((item) => item.id === row.id) === 0}
                  onClick={() => void move(row, -1)}
                />,
                <Button
                  key="down"
                  type="link"
                  icon={<ArrowDownOutlined />}
                  disabled={rows.findIndex((item) => item.id === row.id) === rows.length - 1}
                  onClick={() => void move(row, 1)}
                />,
              ],
            },
          ]}
        />
      </Card>

      <Modal
        open={open}
        title={editing ? '编辑板块' : '新建板块'}
        okText="保存"
        cancelText="取消"
        confirmLoading={create.isPending || persist.isPending}
        onOk={() => void submit()}
        onCancel={() => {
          setOpen(false);
          setEditing(null);
          form.resetFields();
        }}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" initialValues={{ status: 'ENABLED', sortOrder: 0 }}>
          <Form.Item name="sectionType" label="板块类型" rules={[{ required: true, message: '请选择板块类型' }]}>
            <Select options={sectionTypeOptions} />
          </Form.Item>
          <Form.Item name="title" label="标题">
            <Input maxLength={100} placeholder="如：今日特价 / 冷链直达公告" />
          </Form.Item>

          {watchedType === 'CUSTOM' ? (
            <>
              <Form.Item name="kind" label="运营位类型" rules={[{ required: true, message: '请选择运营位类型' }]}>
                <Select options={kindOptions} />
              </Form.Item>
              <Form.Item
                name="content"
                label="正文内容"
                rules={[{ required: true, message: '请填写正文内容' }]}
              >
                <Input.TextArea maxLength={500} rows={3} showCount placeholder="公告 / 弹窗展示的文案" />
              </Form.Item>
              <Form.Item name="linkUrl" label="跳转链接">
                <Input placeholder="选填，如 /products?categoryId=3" />
              </Form.Item>
              <Form.Item name="alwaysShow" label="每次进入都展示（仅弹窗有效）" valuePropName="checked">
                <Switch />
              </Form.Item>
            </>
          ) : (
            <Form.Item name="imageUrl" label="图片地址">
              <Input placeholder="选填，横幅图片 URL" />
            </Form.Item>
          )}

          {watchedType === 'CATEGORY' ? (
            <Form.Item name="categoryId" label="关联分类 ID">
              <InputNumber min={1} style={{ width: '100%' }} placeholder="留空表示展示全部一级分类" />
            </Form.Item>
          ) : null}

          {watchedType === 'FLASH_SALE' || watchedType === 'RECOMMEND' ? (
            <Form.Item name="promotionId" label="关联促销 ID">
              <InputNumber min={1} style={{ width: '100%' }} placeholder="选填，绑定限时活动" />
            </Form.Item>
          ) : null}

          <Form.Item name="sortOrder" label="排序（数值越小越靠前）">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { label: '启用', value: 'ENABLED' },
                { label: '停用', value: 'DISABLED' },
              ]}
            />
          </Form.Item>
          <Form.Item name="range" label="生效时间">
            <DatePicker.RangePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Space className={styles.hint}>停用或超出生效时间的板块不会下发给 Web 商城与小程序。</Space>
        </Form>
      </Modal>
    </PageContainer>
  );
}
