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
package com.oceanbase.odc.service.dlm;

import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.oceanbase.tools.migrator.core.IJobStore;

import lombok.extern.slf4j.Slf4j;

/**
 * @Author：tinker
 * @Date: 2023/5/11 17:51
 * @Descripition:
 */

@Slf4j
@Configuration
public class DLMConfiguration {

    @Value("${odc.task.dlm.thread-pool-size:15}")
    private int dlmThreadPoolSize;

    @Value("${odc.task.dlm.single-task-read-write-ratio:0.5}")
    private double readWriteRatio;

    @Value("${odc.task.dlm.single-task-thread-pool-size:15}")
    private int singleTaskThreadPoolSize;

    @Bean
    public JobMetaFactory jobMetaFactory(IJobStore jobStore) {
        JobMetaFactory jobMetaFactory = new JobMetaFactory();
        jobMetaFactory.setJobStore(jobStore);
        jobMetaFactory.setExecutorService(Executors.newFixedThreadPool(dlmThreadPoolSize));
        jobMetaFactory.setSingleTaskThreadPoolSize(singleTaskThreadPoolSize);
        jobMetaFactory.setReadWriteRatio(readWriteRatio);
        return jobMetaFactory;
    }

    @Bean
    public DataArchiveJobFactory dataArchiveJobFactory(JobMetaFactory jobMetaFactory) {
        return new DataArchiveJobFactory(jobMetaFactory);
    }

}
