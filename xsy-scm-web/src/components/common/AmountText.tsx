import styles from './AmountText.module.css';

function groupInteger(integer: string) {
    const sign = integer.startsWith('-') ? '-' : '';
    const digits = sign ? integer.slice(1) : integer;
    return `${sign}${digits.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}`;
}

export function formatAmount(value: string) {
    const [integer = '0', decimal] = value.split('.');
    return `¥ ${groupInteger(integer)}${decimal === undefined ? '' : `.${decimal}`}`;
}

export function AmountText({value}: { value: string }) {
    return <span className={styles.amount}>{formatAmount(value)}</span>;
}
