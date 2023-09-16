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

package com.oceanbase.odc.service.datatransfer.task;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;

import com.oceanbase.odc.common.lang.Holder;
import com.oceanbase.odc.core.shared.constant.TaskType;
import com.oceanbase.odc.plugin.task.api.datatransfer.DataTransferTask;
import com.oceanbase.odc.plugin.task.api.datatransfer.model.DataTransferFormat;
import com.oceanbase.odc.plugin.task.api.datatransfer.model.DataTransferType;
import com.oceanbase.odc.service.common.util.SpringContextUtil;
import com.oceanbase.odc.service.datatransfer.DataTransferAdapter;
import com.oceanbase.odc.service.datatransfer.LocalFileManager;
import com.oceanbase.odc.service.datatransfer.dumper.DumperOutput;
import com.oceanbase.odc.service.datatransfer.model.DataTransferParameter;
import com.oceanbase.odc.service.flow.task.model.DataTransferTaskResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImportTaskRunner extends BaseTransferTaskRunner {

    public ImportTaskRunner(DataTransferParameter parameter, Holder<DataTransferTask> jobHolder,
            DataTransferAdapter adapter) {
        super(parameter, jobHolder, adapter);
    }

    @Override
    public void preHandle() throws Exception {
        /*
         * move import files
         */
        if (parameter.getTransferType() == DataTransferType.IMPORT) {
            List<String> importFileNames = parameter.getImportFileName();
            if (parameter.isCompressed()) {
                copyImportZip(importFileNames, parameter.getWorkingDir());
            } else {
                copyImportScripts(importFileNames, parameter.getDataTransferFormat(), parameter.getWorkingDir());
            }
        }
    }

    @Override
    protected void postHandle(DataTransferTaskResult result) throws Exception {
        File workingDir = parameter.getWorkingDir();
        if (workingDir == null || !workingDir.exists()) {
            throw new FileNotFoundException("Working dir does not exist");
        }
        File importPath = Paths.get(workingDir.getPath(), "data").toFile();

        if (importPath.exists()) {
            boolean deleteRes = FileUtils.deleteQuietly(importPath);
            log.info("Delete import directory, dir={}, result={}", importPath.getAbsolutePath(), deleteRes);
        }
        for (File subFile : workingDir.listFiles()) {
            if (subFile.isDirectory()) {
                continue;
            }
            boolean deleteRes = FileUtils.deleteQuietly(subFile);
            log.info("Delete import file, fileName={}, result={}", subFile.getName(), deleteRes);
        }
    }

    private void copyImportScripts(List<String> fileNames, DataTransferFormat format, File destDir)
            throws IOException {
        Validate.isTrue(CollectionUtils.isNotEmpty(fileNames), "No script found");
        Validate.notNull(format, "DataTransferFormat can not be null");
        if (DataTransferFormat.CSV.equals(format) && fileNames.size() > 1) {
            log.warn("Multiple files for CSV format is invalid, importFileNames={}", fileNames);
            throw new IllegalArgumentException("Multiple files isn't accepted for CSV format");
        }
        LocalFileManager fileManager = SpringContextUtil.getBean(LocalFileManager.class);
        for (String fileName : fileNames) {
            Optional<File> importFile =
                    fileManager.findByName(TaskType.IMPORT, LocalFileManager.UPLOAD_BUCKET, fileName);
            File from = importFile.orElseThrow(() -> new FileNotFoundException("File not found, " + fileName));
            File dest = new File(destDir.getAbsolutePath() + File.separator + from.getName());
            try (InputStream inputStream = from.toURI().toURL().openStream();
                    OutputStream outputStream = new FileOutputStream(dest)) {
                IOUtils.copy(inputStream, outputStream);
            }
            log.info("Copy script to working dir, from={}, dest={}", from.getAbsolutePath(), dest.getAbsolutePath());
        }
    }

    private void copyImportZip(List<String> fileNames, File destDir) throws IOException {
        if (fileNames == null || fileNames.size() != 1) {
            log.warn("Single zip file is available, importFileNames={}", fileNames);
            throw new IllegalArgumentException("Single zip file is available");
        }
        String fileName = fileNames.get(0);
        LocalFileManager fileManager = SpringContextUtil.getBean(LocalFileManager.class);
        Optional<File> uploadFile = fileManager.findByName(TaskType.IMPORT, LocalFileManager.UPLOAD_BUCKET, fileName);
        File from = uploadFile.orElseThrow(() -> new FileNotFoundException("File not found, " + fileName));
        File dest = new File(destDir.getAbsolutePath() + File.separator + "data");
        FileUtils.forceMkdir(dest);
        DumperOutput dumperOutput = new DumperOutput(from);
        dumperOutput.toFolder(dest);
        log.info("Unzip file to working dir, from={}, dest={}", from.getAbsolutePath(), dest.getAbsolutePath());
    }
}