import { View, Text, Input, Switch } from '@tarojs/components'
import { useState, useEffect } from 'react'
import Taro, { useRouter } from '@tarojs/taro'
import { addressList, addressCreate, addressUpdate } from '../../../services/address'
import type { MallAddress, MallAddressInput } from '../../../types/mall'
import './index.css'

export default function AddressEditPage() {
  const router = useRouter()
  const id = router.params.id ? Number(router.params.id) : undefined

  const [receiverName, setReceiverName] = useState('')
  const [phone, setPhone] = useState('')
  const [region, setRegion] = useState('')
  const [detailAddress, setDetailAddress] = useState('')
  const [defaultAddress, setDefaultAddress] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (id === undefined) return
    addressList()
      .then((list) => {
        const found = list.find((a) => a.id === id) as MallAddress | undefined
        if (found) {
          setReceiverName(found.receiverName)
          setPhone(found.phone)
          setRegion(found.region)
          setDetailAddress(found.detailAddress)
          setDefaultAddress(found.defaultAddress)
        }
      })
      .catch(() => undefined)
  }, [id])

  const onSave = async () => {
    if (!receiverName.trim()) return Taro.showToast({ title: '请填写收货人', icon: 'none' })
    if (!phone.trim()) return Taro.showToast({ title: '请填写联系电话', icon: 'none' })
    if (!region.trim()) return Taro.showToast({ title: '请填写所在地区', icon: 'none' })
    if (!detailAddress.trim()) return Taro.showToast({ title: '请填写详细地址', icon: 'none' })

    const payload: MallAddressInput = {
      receiverName: receiverName.trim(),
      phone: phone.trim(),
      region: region.trim(),
      detailAddress: detailAddress.trim(),
      defaultAddress,
    }

    setSaving(true)
    try {
      if (id === undefined) await addressCreate(payload)
      else await addressUpdate(id, payload)
      Taro.showToast({ title: '已保存', icon: 'success' })
      setTimeout(() => Taro.navigateBack(), 500)
    } catch {
      /* toast by request layer */
    } finally {
      setSaving(false)
    }
  }

  return (
    <View className="addr-edit">
      <View className="field">
        <Text className="field__label">收货人</Text>
        <Input
          className="field__input"
          placeholder="请输入收货人姓名"
          value={receiverName}
          onInput={(e) => setReceiverName(e.detail.value)}
        />
      </View>
      <View className="field">
        <Text className="field__label">手机号</Text>
        <Input
          className="field__input"
          placeholder="请输入联系电话"
          value={phone}
          onInput={(e) => setPhone(e.detail.value)}
        />
      </View>
      <View className="field">
        <Text className="field__label">所在地区</Text>
        <Input
          className="field__input"
          placeholder="省/市/区"
          value={region}
          onInput={(e) => setRegion(e.detail.value)}
        />
      </View>
      <View className="field field--column">
        <Text className="field__label">详细地址</Text>
        <Input
          className="field__input"
          placeholder="街道、门牌号等"
          value={detailAddress}
          onInput={(e) => setDetailAddress(e.detail.value)}
        />
      </View>
      <View className="field">
        <Text className="field__label">设为默认</Text>
        <Switch checked={defaultAddress} onChange={(e) => setDefaultAddress(e.detail.value)} />
      </View>

      <View className="addr-edit__save" onClick={onSave}>
        <Text>{saving ? '保存中...' : '保存'}</Text>
      </View>
    </View>
  )
}
