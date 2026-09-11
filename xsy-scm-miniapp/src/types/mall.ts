// 商城领域类型定义，对应后端 com.xianshuyuan.scm.mall.vo / dto。

export type PriceSource = 'AGREEMENT' | 'MARKET' | 'OVERRIDE'
export type ProductType = 'STANDARD' | 'NON_STANDARD'
export type OrderStatus = 'DRAFT' | 'PENDING' | 'CONFIRMED' | 'CANCELLED'
export type OrderSource = 'NORMAL' | 'SUPPLEMENT' | 'MALL'

export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

export const MallErrorCode = {
  LOGIN_FAILED: 40170,
  LOGIN_REQUIRED: 40171,
  TOKEN_INVALID: 40172,
  ACCOUNT_DISABLED: 40370,
  CUSTOMER_DISABLED: 40371,
  SKU_NOT_VISIBLE: 40372,
  SKU_NOT_FOUND: 40470,
  CART_EMPTY: 40070,
  INVALID_QUANTITY: 40071,
  QUANTITY_OUT_OF_RANGE: 40072,
  ADDRESS_NOT_FOUND: 40471,
  ADDRESS_REQUIRED: 40073,
  ORDER_ITEMS_REQUIRED: 40074,
  ORDER_NOT_FOUND: 40472,
  PRICE_CHANGED: 40970,
  WECHAT_LOGIN_UNAVAILABLE: 50170,
} as const

export interface MallProfile {
  accountId: number
  customerId: number
  customerCode: string
  customerName: string
  username: string
  wechatBound: boolean
}

export interface MallLoginResult {
  token: string
  expiresAt: string
  profile: MallProfile
}

export interface MallCategory {
  id: number
  parentId: number | null
  name: string
  level: number
  sortOrder: number
  productCount: number
}

export type SpecValue = Record<string, string>

export interface MallProduct {
  skuId: number
  spuId: number
  productName: string
  skuCode: string
  specName: string
  specValues: SpecValue
  saleUnit: string
  productType: ProductType
  categoryId: number | null
  categoryName: string
  marketPrice: string
  unitPrice: string
  priceSource: PriceSource
}

export type MallCardStyle = 'FLAT' | 'SHADOW' | 'BORDER'
export type MallProductCardStyle = 'COMPACT' | 'COMFORTABLE' | 'SPACIOUS'
export type MallNavigationStyle = 'BOTTOM' | 'TOP' | 'SIDEBAR'
export type MallHomeSectionType =
  | 'BANNER'
  | 'FLASH_SALE'
  | 'NEW_ARRIVAL'
  | 'CATEGORY'
  | 'RECOMMEND'
  | 'CUSTOM'
  | (string & Record<never, never>)

export interface MallThemeConfig {
  themeCode: string
  primaryColor: string
  accentColor: string
  pageBackground: string
  cardRadius: number
  cardStyle: MallCardStyle
  productCardStyle: MallProductCardStyle
  navigationStyle: MallNavigationStyle
}

export interface MallHomeProduct {
  skuId?: number
  productName?: string
  categoryName?: string
  specName?: string
  saleUnit?: string
  imageUrl?: string | null
  unitPrice?: string | null
  marketPrice?: string | null
  priceSource?: PriceSource | string | null
}

export interface MallHomeSectionPayload {
  title?: string
  description?: string
  imageUrl?: string
  targetUrl?: string
  notice?: string
  text?: string
  products?: MallHomeProduct[]
  categories?: MallCategory[]
  items?: Array<Record<string, unknown>>
  [key: string]: unknown
}

export interface MallHomeSection {
  id: number
  sectionType: MallHomeSectionType
  title: string | null
  sortOrder: number | null
  payload: MallHomeSectionPayload | null
  promotionId: number | null
  categoryId: number | null
}

export interface MallPromotion {
  id: number
  name: string
  type: string
  scopeType: string
  scopeIds: unknown
  thresholdAmount: string | number | null
  discountRate: string | number | null
  reduceAmount: string | number | null
  promoPrice: string | number | null
  giftSkuId: number | null
  giftQuantity: string | number | null
  limitQuantity: string | number | null
  startAt: string | null
  endAt: string | null
  status: string
  priority: number | null
  description: string | null
  effective: boolean
}

export interface MallHomeResponse {
  theme: MallThemeConfig
  sections: MallHomeSection[]
  categories: MallCategory[]
  promotions: MallPromotion[]
  generatedAt: string
}

export interface PageData<T> {
  records: T[]
  page: number
  pageSize: number
  total: number
}

export interface MallCartItem {
  skuId: number
  productName: string
  specName: string
  specValues: SpecValue
  saleUnit: string
  productType: ProductType
  quantity: string
  unitPrice: string
  priceSource: PriceSource
  lineAmount: string
  available: boolean
  reason: string | null
}

export interface MallCart {
  items: MallCartItem[]
  totalQuantity: string
  totalAmount: string
  unavailableCount: number
}

export interface MallCheckoutItem {
  skuId: number
  productName: string
  specName: string
  saleUnit: string
  productType: ProductType
  quantity: string
  unitPrice: string
  priceSource: PriceSource
  lineAmount: string
}

export interface MallAddress {
  id: number
  receiverName: string
  phone: string
  region: string
  detailAddress: string
  defaultAddress: boolean
}

export interface MallCheckoutPreview {
  items: MallCheckoutItem[]
  totalQuantity: string
  totalAmount: string
  priceFingerprint: string
  address: MallAddress | null
  unavailableCount: number
}

export interface MallOrderItem {
  id: number
  skuId: number
  productName: string
  specName: string
  specValues: SpecValue
  saleUnit: string
  productType: ProductType
  orderedQuantity: string
  actualQuantity: string
  unitPrice: string
  priceSource: PriceSource
  amount: string
}

export interface MallOrder {
  id: number
  orderNo: string
  status: OrderStatus
  source: OrderSource
  totalAmount: string
  createdAt: string
  submittedAt: string | null
  confirmedAt: string | null
  items: MallOrderItem[]
}

export interface MallOrderSubmitResult {
  orderId: number
  orderNo: string
  status: OrderStatus
  totalAmount: string
}

// 请求体相关
export interface MallAddressInput {
  receiverName: string
  phone: string
  region: string
  detailAddress: string
  defaultAddress: boolean
}

export interface MallCheckoutItemInput {
  skuId: number
  quantity: string
}

export interface MallOrderSubmitInput {
  items: MallCheckoutItemInput[]
  addressId: number
  priceFingerprint: string
}
