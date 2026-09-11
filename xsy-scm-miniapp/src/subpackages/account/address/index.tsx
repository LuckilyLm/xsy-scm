import { View, Text } from '@tarojs/components'
import { useState } from 'react'
import Taro, { useDidShow } from '@tarojs/taro'
import { addressList, addressSetDefault, addressDelete } from '../../../services/address'
import EmptyState from '../../../components/EmptyState'
import type { MallAddress } from '../../../types/mall'
import './index.css'

export default function AddressListPage() {
  const [list, setList] = useState<MallAddress[]>([])

  const load = async () => {
    try {
      setList(await addressList())
    } catch {
      /* toast by request layer */
    }
  }

  useDidShow(() => load())

  const onSetDefault = async (id: number) => {
    try {
      const updated = await addressSetDefault(id)
      setList((prev) => prev.map((a) => ({ ...a, defaultAddress: a.id === updated.id })))
    } catch {
      /* toast by request layer */
    }
  }

  const onDelete = (id: number) => {
    Taro.showModal({
      title: '删除地址',
      content: '确定删除该收货地址？',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await addressDelete(id)
          load()
        } catch {
          /* toast by request layer */
        }
      },
    })
  }

  const goEdit = (id?: number) =>
    Taro.navigateTo({
      url: `/subpackages/account/address-edit/index${id ? `?id=${id}` : ''}`,
    })

  return (
    <View className="addr">
      {list.length === 0 ? (
        <EmptyState text="还没有收货地址" />
      ) : (
        list.map((a) => (
          <View key={a.id} className="addr__item">
            <View className="addr__head">
              <Text className="addr__name">{a.receiverName}</Text>
              <Text className="addr__phone">{a.phone}</Text>
              {a.defaultAddress && <Text className="addr__default">默认</Text>}
            </View>
            <View className="addr__detail">
              <Text>
                {a.region} {a.detailAddress}
              </Text>
            </View>
            <View className="addr__actions">
              {!a.defaultAddress && (
                <Text className="addr__act" onClick={() => onSetDefault(a.id)}>
                  设为默认
                </Text>
              )}
              <Text className="addr__act" onClick={() => goEdit(a.id)}>
                编辑
              </Text>
              <Text className="addr__act addr__act--danger" onClick={() => onDelete(a.id)}>
                删除
              </Text>
            </View>
          </View>
        ))
      )}

      <View className="addr__add" onClick={() => goEdit()}>
        <Text>+ 新增收货地址</Text>
      </View>
    </View>
  )
}
