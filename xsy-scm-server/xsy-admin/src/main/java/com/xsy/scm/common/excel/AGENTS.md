# Excel公共层

- 本目录只处理工作簿类型与格式，不依赖具体业务域。
- ScmExcel.row保留数值、文本身份和空值语义；客户模板写数值使用setNumber，避免无提示损失Excel精度。现行规则见根目录docs/decisions.md。
- 改动时同步检查ScmExcelNumericTest和ScmStatementExportLabelsTest，既验证金额可计算，也保留长编码与未知值断言。
