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

package com.oceanbase.odc.plugin.task.api.datatransfer;

import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.oceanbase.tools.loaddump.common.model.ObjectStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DataTransferResult {

    private Map<String, OutputStream> fileName2OutputStreams;
    private List<ObjectStatus> dataObjectsInfo = new LinkedList<>();
    private List<ObjectStatus> schemaObjectsInfo = new LinkedList<>();

    public static DataTransferResult of(DataTransferContext context) {
        DataTransferResult result = new DataTransferResult();
        if (context == null) {
            return result;
        }
        result.getSchemaObjectsInfo().addAll(context.getSchemaObjectsInfo());
        result.getDataObjectsInfo().addAll(context.getDataObjectsInfo());
        return result;
    }

}
