import type {PropsWithChildren, ReactNode} from 'react';
import styles from './PageContainer.module.css';

interface PageContainerProps extends PropsWithChildren {
    title?: ReactNode;
}

export function PageContainer({title, children}: PageContainerProps) {
    return (
        <section className={styles.container}>
            {title ? <header className={styles.header}>{title}</header> : null}
            {children}
        </section>
    );
}
