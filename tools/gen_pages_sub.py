#!/usr/bin/env python3
"""
生成 pages-sub 分包下的占位页面骨架。

Step 2 要求 pages.json 引用到的每个页面都必须真实存在，否则 uni-app 构建失败。
本脚本按规划文档 §7 页面树一次性铺出 P0/P1 所需的分包页面骨架。

安全约束
--------
默认**跳过已存在的文件**，绝不覆盖已开发的页面。页面进入垂直切片开发后，
本脚本对该页面即自动失效，不会造成回退。确需重建时才加 --force。

用法
----
    python tools/gen_pages_sub.py            # 只补缺失的页面
    python tools/gen_pages_sub.py --force    # 强制重写全部占位页（会覆盖！）
"""

import argparse
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PAGES_SUB = os.path.join(ROOT, 'xsy-scm-miniapp', 'src', 'pages-sub')

# (相对路径, 占位标记, 页面标题, 说明, 规划出处)
PAGES = [
    ('auth/bind', '客户绑定', '客户绑定与审核状态',
     '微信授权后的客户绑定，以及审核中 / 审核未通过的状态展示。', '规划 §31 / §32'),

    ('product/search', '搜索', '商品搜索',
     '搜索历史、热门搜索、搜索结果与无结果状态。', '规划 §13'),
    ('product/detail', '商品详情', '商品详情',
     '规格、客户价、价格来源、库存状态、非标品说明、配送说明与加购物车。', '规划 §11'),

    ('checkout/index', '结算', '结算',
     '地址、配送时间、商品明细、非标品预估、优惠、结算方式、订单备注与价格变化确认。', '规划 §17'),
    ('checkout/success', '下单成功', '下单成功',
     '提交结果、订单号与后续动作入口。', '规划 §17'),

    ('address/list', '收货地址', '收货地址',
     '地址列表、设为默认，以及新增 / 编辑入口。', '规划 §20'),
    ('address/edit', '地址编辑', '地址编辑',
     '收货人、联系电话、所在区域与详细地址维护。', '规划 §20'),

    ('order/detail', '订单详情', '订单详情',
     '突出「买了什么 / 订了多少 / 最后称了多少 / 多少钱 / 什么时候送 / 送到哪 / 现在到哪了」。', '规划 §18.1'),
    ('order/delivery', '配送详情', '配送详情',
     '配送进度与配送信息。', '规划 §18'),
    ('order/weigh', '实重明细', '实重明细',
     '逐行展示下单量与实际称重量、差额与最终金额。', '规划 §19'),

    ('favorite/index', '常购', '常购商品',
     '常购商品、最近采购与快速加购。', '规划 §14'),
    ('favorite/reorder', '再来一单', '再来一单确认',
     '按历史订单回填购物车，并处理失效 / 缺货 / 改价异常。', '规划 §15'),

    ('promotion/flash-sale', '限时抢购', '限时抢购',
     '活动商品与活动价。', '规划 §9.2'),

    ('account/profile', '客户资料', '客户资料',
     '客户名称、门店、业务员与客户类型等资料展示。', '规划 §20'),
    ('account/credit', '账期', '账期',
     '可用账期与账期明细。', '规划 §20'),
    ('account/balance', '余额', '余额',
     '账户余额与流水。', '规划 §20'),
    ('account/coupon', '优惠券', '优惠券',
     '可用 / 已使用 / 已过期优惠券。', '规划 §20'),
    ('account/bill', '对账', '对账',
     '待对账与历史对账。', '规划 §20'),
    ('account/stat', '下单统计', '下单统计',
     '按周期的下单金额与频次统计。', '规划 §20'),
    ('account/message', '消息', '消息通知',
     '订单状态、配送与活动等通知。', '规划 §20'),
    ('account/terms', '服务条款', '服务条款',
     '静态条款内容。', '规划 §20'),
    ('account/after-sale-rule', '售后规则', '售后规则',
     '静态售后规则内容。', '规划 §20'),
    ('account/about', '关于我们', '关于我们',
     '平台介绍与版本信息。', '规划 §20'),
    ('account/setting', '设置', '设置',
     '账号与绑定、缓存清理与退出登录。', '规划 §20'),
]

TEMPLATE = '''<template>
  <view class="{slug}">
    <PagePlaceholder
      mark="{mark}"
      title="{title}"
      desc="{desc}"
      plan-ref="{plan_ref}"
    />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .{slug} {{
    min-height: 100vh;
    background-color: $color-bg-page;
  }}
</style>
'''


def main():
    parser = argparse.ArgumentParser(description='生成 pages-sub 占位页面骨架')
    parser.add_argument('--force', action='store_true',
                        help='强制重写已存在的页面（会覆盖已开发内容）')
    args = parser.parse_args()

    written = skipped = 0
    for rel, mark, title, desc, plan_ref in PAGES:
        slug = rel.replace('/', '-')
        target = os.path.join(PAGES_SUB, f'{rel}.vue')

        if os.path.exists(target) and not args.force:
            skipped += 1
            continue

        os.makedirs(os.path.dirname(target), exist_ok=True)
        with open(target, 'w', encoding='utf-8', newline='\n') as fp:
            fp.write(TEMPLATE.format(
                slug=slug, mark=mark, title=title, desc=desc, plan_ref=plan_ref,
            ))
        written += 1
        print('  +', os.path.relpath(target, ROOT).replace('\\', '/'))

    print(f'新增 {written} 个，跳过已存在 {skipped} 个（共 {len(PAGES)} 个）')


if __name__ == '__main__':
    main()
