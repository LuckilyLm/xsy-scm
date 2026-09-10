import {PageContainer} from '../components/common/PageContainer';

export function PlaceholderPage({title}: { title: string }) {
    return <PageContainer title={title}><p>该模块将在后续迭代中开放。</p></PageContainer>;
}
