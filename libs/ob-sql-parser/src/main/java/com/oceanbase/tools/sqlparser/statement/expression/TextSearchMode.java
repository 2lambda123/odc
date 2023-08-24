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
package com.oceanbase.tools.sqlparser.statement.expression;

import lombok.Getter;

/**
 * {@link TextSearchMode}
 *
 * @author yh263208
 * @date 2022-12-08 17:59
 * @since ODC_release_4.1.0
 */
@Getter
public enum TextSearchMode {
    // in boolean mode
    BOOLEAN_MODE("IN BOOLEAN MODE"),
    // in natural language mode
    NATURAL_LANGUAGE_MODE("IN NATURAL LANGUAGE MODE");

    private final String value;

    TextSearchMode(String value) {
        this.value = value;
    }
}