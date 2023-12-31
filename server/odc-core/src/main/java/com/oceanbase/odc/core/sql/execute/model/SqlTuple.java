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
package com.oceanbase.odc.core.sql.execute.model;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.oceanbase.odc.common.util.TraceWatch;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * Asynchronous sql tuple, used to encapsulate sqlid, original sql and actual executed sql
 * collection
 *
 * @author yh263208
 * @date 2021-11-21 19:26
 * @since ODC_release_3.2.2
 */
@Getter
@EqualsAndHashCode
@ToString
public class SqlTuple {

    @Getter(AccessLevel.NONE)
    private int copiedTimes = 0;
    private final String sqlId;
    private final String originalSql;
    private final String executedSql;
    @JsonProperty(access = Access.WRITE_ONLY)
    private final TraceWatch sqlWatch;

    private SqlTuple(@NonNull String sqlId, @NonNull String originalSql, @NonNull String executedSql,
            @NonNull TraceWatch sqlWatch) {
        this.sqlId = sqlId;
        this.originalSql = originalSql;
        this.executedSql = executedSql;
        this.sqlWatch = sqlWatch;
    }

    public SqlTuple softCopy() {
        String sqlId = this.sqlId + "-" + (++this.copiedTimes);
        return new SqlTuple(sqlId, this.originalSql, this.executedSql, this.sqlWatch);
    }

    public static SqlTuple newTuple(@NonNull String originalSql, @NonNull String executedSql) {
        TraceWatch traceWatch = new TraceWatch("SQL-EXEC");
        return new SqlTuple(generateSqlId(), originalSql, executedSql, traceWatch);
    }

    public static SqlTuple newTuple(@NonNull String originalSql, @NonNull String executedSql,
            @NonNull TraceWatch traceWatch) {
        return new SqlTuple(generateSqlId(), originalSql, executedSql, traceWatch);
    }

    public static SqlTuple newTuple(@NonNull String originalAndExecutedSql) {
        TraceWatch traceWatch = new TraceWatch("SQL-EXEC");
        return new SqlTuple(generateSqlId(), originalAndExecutedSql, originalAndExecutedSql, traceWatch);
    }

    public static List<SqlTuple> newTuples(String... originalAndExecutedSqls) {
        List<SqlTuple> sqlTuples = new LinkedList<>();
        for (String originalAndExecutedSql : originalAndExecutedSqls) {
            sqlTuples.add(newTuple(originalAndExecutedSql));
        }
        return sqlTuples;
    }

    public static List<SqlTuple> newTuples(Collection<String> originalAndExecutedSqls) {
        List<SqlTuple> sqlTuples = new LinkedList<>();
        for (String originalAndExecutedSql : originalAndExecutedSqls) {
            sqlTuples.add(newTuple(originalAndExecutedSql));
        }
        return sqlTuples;
    }

    private static String generateSqlId() {
        return UUID.randomUUID().toString().replaceAll("-", "").toUpperCase();
    }

}
