/*
 * Copyright (c) 2023 OceanBase.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oceanbase.odc.service.sqlcheck.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.service.sqlcheck.SqlCheckContext;
import com.oceanbase.odc.service.sqlcheck.SqlCheckRule;
import com.oceanbase.odc.service.sqlcheck.SqlCheckUtil;
import com.oceanbase.odc.service.sqlcheck.model.CheckViolation;
import com.oceanbase.odc.service.sqlcheck.model.SqlCheckRuleType;
import com.oceanbase.tools.sqlparser.statement.Statement;
import com.oceanbase.tools.sqlparser.statement.common.RelationFactor;
import com.oceanbase.tools.sqlparser.statement.createtable.CreateTable;
import com.oceanbase.tools.sqlparser.statement.createtable.SetComment;

import lombok.NonNull;

/**
 * {@link OracleNoTableCommentExists}
 *
 * @author yh263208
 * @date 2023-07-31 15:03
 * @since ODC_release_4.2.0
 */
public class OracleNoTableCommentExists implements SqlCheckRule {

    private final String currentSchema;

    public OracleNoTableCommentExists(String currentSchema) {
        this.currentSchema = currentSchema;
    }

    @Override
    public SqlCheckRuleType getType() {
        return SqlCheckRuleType.NO_TABLE_COMMENT_EXISTS;
    }

    @Override
    public List<CheckViolation> check(@NonNull Statement statement, @NonNull SqlCheckContext context) {
        if (context.getCurrentStmtIndex() < context.getTotalStmtCount() - 1) {
            return Collections.emptyList();
        }
        // 已经检测到了最后一个 sql
        Map<String, List<CreateTable>> tableName2Table = context.getAllCheckedStatements(CreateTable.class)
                .stream().collect(Collectors.groupingBy(this::getKey));
        List<SetComment> setComments = context.getAllCheckedStatements(SetComment.class);
        if (statement instanceof CreateTable) {
            CreateTable c = (CreateTable) statement;
            List<CreateTable> createTables = tableName2Table.computeIfAbsent(getKey(c), s -> new ArrayList<>());
            createTables.add(c);
        } else if (statement instanceof SetComment) {
            setComments.add((SetComment) statement);
        }
        setComments.stream().filter(s -> s.getTable() != null).forEach(s -> {
            RelationFactor t = s.getTable();
            String tableName = SqlCheckUtil.unquoteOracleIdentifier(t.getRelation());
            if (t.getSchema() == null) {
                tableName2Table.remove(currentSchema == null ? tableName : currentSchema + "." + tableName);
            } else {
                tableName2Table.remove(SqlCheckUtil.unquoteOracleIdentifier(t.getSchema()) + "." + tableName);
            }
        });
        return tableName2Table.values().stream().map(createTables -> {
            CreateTable stmt = createTables.get(0);
            return SqlCheckUtil.buildViolation(stmt.getText(), stmt, getType(), new Object[] {stmt.getTableName()});
        }).collect(Collectors.toList());
    }

    @Override
    public List<DialectType> getSupportsDialectTypes() {
        return Collections.singletonList(DialectType.OB_ORACLE);
    }

    private String getKey(CreateTable c) {
        String tableName = SqlCheckUtil.unquoteOracleIdentifier(c.getTableName());
        if (c.getSchema() == null) {
            return currentSchema == null ? tableName : currentSchema + "." + tableName;
        }
        return SqlCheckUtil.unquoteOracleIdentifier(c.getSchema()) + "." + tableName;
    }

}
