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

import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.pf4j.ExtensionPoint;

import com.oceanbase.odc.plugin.task.api.datatransfer.model.DataTransferConfig;
import com.oceanbase.odc.plugin.task.api.datatransfer.model.DataTransferFormat;
import com.oceanbase.odc.plugin.task.api.datatransfer.model.DatabaseConfig;
import com.oceanbase.odc.plugin.task.api.datatransfer.model.UploadFileResult;
import com.oceanbase.tools.loaddump.common.enums.ObjectType;

import lombok.NonNull;

public interface DataTransferExtensionPoint extends ExtensionPoint {

    DataTransferRunner generate(@NonNull DataTransferConfig config,
            @NonNull File workingDir, @NonNull File logDir,
            @NonNull Map<String, InputStream> importFileName2InputStream);

    Map<ObjectType, Set<String>> getExportObjectNames(
            @NonNull DatabaseConfig databaseConfig, @NonNull Set<ObjectType> objectTypes);

    Set<DataTransferFormat> getSupportedTransferFormats();

    UploadFileResult getImportFileInfo(@NonNull String fileName, @NonNull InputStream inputStream);

}
