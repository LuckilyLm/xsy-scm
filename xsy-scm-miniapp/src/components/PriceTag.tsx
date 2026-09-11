import { Text } from '@tarojs/components'
import { formatPrice } from '../utils/format'

interface Props {
  value: string | number | null
  size?: 'sm' | 'md' | 'lg'
  prefix?: string
}

export default function PriceTag({ value, size = 'md', prefix = '¥' }: Props) {
  return <Text className={`price price-${size}`}>{formatPrice(value, prefix)}</Text>
}
