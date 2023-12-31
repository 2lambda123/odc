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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.service.sqlcheck.SqlCheckContext;
import com.oceanbase.odc.service.sqlcheck.SqlCheckRule;
import com.oceanbase.odc.service.sqlcheck.SqlCheckUtil;
import com.oceanbase.odc.service.sqlcheck.model.CheckViolation;
import com.oceanbase.odc.service.sqlcheck.model.SqlCheckRuleType;
import com.oceanbase.odc.service.sqlcheck.parser.DropStatement;
import com.oceanbase.tools.sqlparser.statement.Statement;

import lombok.NonNull;

/**
 * {@link RestrictDropObjectTypes}
 *
 * @author yh263208
 * @date 2023-07-25 15:19
 * @since ODC_release_4.2.0
 */
public class RestrictDropObjectTypes implements SqlCheckRule {

    private final Set<String> allowObjectTypes;

    public RestrictDropObjectTypes(@NonNull Set<String> allowObjectTypes) {
        this.allowObjectTypes = allowObjectTypes;
    }

    @Override
    public SqlCheckRuleType getType() {
        return SqlCheckRuleType.RESTRICT_DROP_OBJECT_TYPES;
    }

    @Override
    public List<CheckViolation> check(@NonNull Statement statement, @NonNull SqlCheckContext context) {
        if (!(statement instanceof DropStatement)) {
            return Collections.emptyList();
        }
        String objectType = ((DropStatement) statement).getObjectType();
        if (allowObjectTypes.stream().noneMatch(s -> StringUtils.equalsIgnoreCase(s, objectType))) {
            String allTypes = allowObjectTypes.isEmpty() ? "N/A" : String.join(",", allowObjectTypes);
            return Collections.singletonList(SqlCheckUtil.buildViolation(
                    statement.getText(), statement, getType(), new Object[] {objectType, allTypes}));
        }
        return Collections.emptyList();
    }

    @Override
    public List<DialectType> getSupportsDialectTypes() {
        return Arrays.asList(DialectType.OB_ORACLE, DialectType.MYSQL,
                DialectType.OB_MYSQL, DialectType.ODP_SHARDING_OB_MYSQL);
    }

}
