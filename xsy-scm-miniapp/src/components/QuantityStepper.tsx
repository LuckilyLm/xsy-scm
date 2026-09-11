import { View, Input } from '@tarojs/components'

interface Props {
  value: number
  min?: number
  max?: number
  step?: number
  disabled?: boolean
  onChange: (value: number) => void
}

export default function QuantityStepper({
  value,
  min = 1,
  max = 999999,
  step = 1,
  disabled = false,
  onChange,
}: Props) {
  const round = (n: number) => Math.round(n * 10000) / 10000
  const clamp = (n: number) => Math.min(max, Math.max(min, n))

  const dec = () => {
    if (!disabled && value > min) onChange(clamp(round(value - step)))
  }
  const inc = () => {
    if (!disabled && value < max) onChange(clamp(round(value + step)))
  }
  const onInput = (e: { detail: { value: string } }) => {
    if (disabled) return
    const n = Number(e.detail.value)
    if (!Number.isNaN(n)) onChange(clamp(n))
  }

  return (
    <View className="stepper">
      <View className="stepper__btn" onClick={dec}>
        −
      </View>
      <Input
        className="stepper__input"
        type="digit"
        value={String(value)}
        disabled={disabled}
        onInput={onInput}
      />
      <View className="stepper__btn" onClick={inc}>
        +
      </View>
    </View>
  )
}
