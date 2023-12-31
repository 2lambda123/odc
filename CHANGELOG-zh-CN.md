# OceanBase Developer Center (ODC) CHANGELOG

## 4.2.1 (2023-09-25)

### 缺陷修复

SQL 执行

- OceanBase 4.0 之前版本时无法打印 DBMS 输出
- 编辑结果集时 DML 语句生成速度缓慢

导入导出

- 任务执行过程中导入/导出对象在任务详情中不显示

PL 调试

- OceanBase 多节点部署时 PL 调试偶现无法连接到数据库
- OceanBase Oracle 模式小写 schema 下调试匿名块时获取数据库连接错误

数据源管理

- 回收站模块生成的针对索引的 flashback 语句执行报错
- 会话管理界面无法显示会话正在执行的 SQL
- 批量导入连接时模版文件中存在空行或空列时引发空指针异常

数据脱敏

- OceanBase MySQL 模式配置为大小写敏感时，敏感列无法区分大小写

工单管理

- 工单提交后工单状态长期处于“排队中”不更新且工单不报错

第三方集成

- 审批流未包含外部审批节点时也会尝试获取外部审批工单的 ID

SQL-Check

- OceanBase Oracle 模式下无法检测表或列的注释是否存在

### 安全加固

- 解决第三方集成过程中可能发生的 SSRF 攻击