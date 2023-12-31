# OceanBase Developer Center (ODC) CHANGELOG

## 4.2.1 (2023-09-25)

### Fix

SQL Execution

- Unable to print DBMS output when OceanBase is older than 4.0
- DML statement generation is slow when editing result sets

Import & Export

- Import/export objects are not displayed in task details during task execution

PL Debugging

- When OceanBase is deployed on multiple nodes, PL debugging occasionally fails to connect to the database
- Obtaining database connection errors when debugging anonymous blocks under OceanBase Oracle mode lowercase schema

Datasource management

- An error occurs when the flashback statement generated by the recycle bin module for the index is executed
- The session management interface cannot display the SQL being executed by the session
- A null pointer exception occurs when there are empty rows or columns in the template file during batch import connections

Data desensitization

- When the OceanBase MySQL schema is configured as case-sensitive, sensitive columns cannot be case-sensitive

Work order management

- After the work order is submitted, the work order status remains "queued" for a long time and is not updated and the work order does not report an error

Third party integration

- When the approval flow does not contain an external approval node, it will also try to obtain the ID of the external approval work order

SQL-Check

- OceanBase Oracle mode cannot detect whether table or column comments exist

### Security reinforcement

- Address possible SSRF attacks during third-party integration
