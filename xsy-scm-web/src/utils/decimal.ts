const DECIMAL_SCALE = 4;
const DECIMAL_PATTERN = /^\d+(\.\d{1,4})?$/;

function toScaledInteger(value: string): bigint {
  if (!DECIMAL_PATTERN.test(value)) {
    throw new Error('Invalid decimal string');
  }

  const [integer, fraction = ''] = value.split('.');
  return BigInt(integer) * 10_000n
    + BigInt(fraction.padEnd(DECIMAL_SCALE, '0'));
}

function fromScaledInteger(value: bigint): string {
  const sign = value < 0n ? '-' : '';
  const absolute = value < 0n ? -value : value;
  const integer = absolute / 10_000n;
  const fraction = (absolute % 10_000n).toString().padStart(DECIMAL_SCALE, '0');
  return `${sign}${integer}.${fraction}`;
}

export function isPositiveDecimal(value: string): boolean {
  return DECIMAL_PATTERN.test(value) && toScaledInteger(value) > 0n;
}

export function subtractDecimal(left: string, right: string): string {
  return fromScaledInteger(toScaledInteger(left) - toScaledInteger(right));
}
