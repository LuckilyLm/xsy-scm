export interface DataTracerRecord {
    dataTracerId: number;
    content: string | null;
    contentText?: string | null;
    diffOld?: string | null;
    diffNew?: string | null;
    createTime?: string;
    userName?: string;
    userType?: number;
    ipRegion?: string;
    ip?: string;
    browser?: string;
    os?: string;
    device?: string;
}
