import { View, Text } from '@tarojs/components'

interface Props {
  text?: string
}

export default function EmptyState({ text = '暂无数据' }: Props) {
  return (
    <View className="empty-state">
      <Text>{text}</Text>
    </View>
  )
}
