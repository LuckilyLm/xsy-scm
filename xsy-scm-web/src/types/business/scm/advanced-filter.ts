export interface ScmAdvancedFilterField {
  key: string;
  title: string;
  operator: 'EQ' | 'CONTAINS';
  valueType: 'ID' | 'TEXT' | 'BOOLEAN';
}
export interface ScmAdvancedFilterCondition {
  key: string;
  operator: 'EQ' | 'CONTAINS';
  value: string;
}
