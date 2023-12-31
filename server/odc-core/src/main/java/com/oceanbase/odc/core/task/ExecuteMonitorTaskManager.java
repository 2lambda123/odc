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
package com.oceanbase.odc.core.task;

import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.oceanbase.odc.core.sql.execute.task.SqlExecuteCallable;
import com.oceanbase.odc.core.sql.execute.task.SqlExecuteTaskManager;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Asynchronous task execution monitoring task manager is mainly used to manage tasks generated by
 * other asynchronous {@link DefaultTaskManager}. At this stage, it is mainly used for task timeout
 * management
 *
 * @author yh263208
 * @date 2021-11-11 20:34
 * @since ODC_release_3.2.2
 * @see DefaultTaskManager
 */
@Slf4j
public class ExecuteMonitorTaskManager extends DefaultTaskManager {

    private final static int MAX_MONITOR_TASK_COUNT = 2;
    private final DelayQueue<TaskTimeoutInfo> delayQueue = new DelayQueue<>();

    public ExecuteMonitorTaskManager() {
        super(new ThreadPoolExecutor(
                MAX_MONITOR_TASK_COUNT, MAX_MONITOR_TASK_COUNT,
                0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new TaskThreadFactory("execute-monitor"),
                new ThreadPoolExecutor.AbortPolicy()));
        TimeoutMonitorTask monitorTask = new TimeoutMonitorTask(delayQueue);
        this.submit(monitorTask);
    }

    @Override
    public Future<?> submit(@NonNull Runnable runnable) {
        if (getLiveTaskCount() >= MAX_MONITOR_TASK_COUNT) {
            throw new IllegalStateException("Too many tasks submitted, max=" + MAX_MONITOR_TASK_COUNT);
        }
        return super.submit(runnable);
    }

    @Override
    public <T> Future<T> submit(@NonNull Callable<T> callable) {
        if (getLiveTaskCount() >= MAX_MONITOR_TASK_COUNT) {
            throw new IllegalStateException("Too many tasks submitted, max=" + MAX_MONITOR_TASK_COUNT);
        }
        return super.submit(callable);
    }

    public Future<?> submit(@NonNull Runnable runnable, long timeout, TimeUnit timeUnit,
            @NonNull TaskManager taskManager) {
        Future<?> future = taskManager.submit(runnable);
        monitorTimeout(future, timeout, timeUnit);
        return future;
    }

    public <T> Future<T> submit(@NonNull Callable<T> callable, long timeout, TimeUnit timeUnit,
            @NonNull TaskManager taskManager) {
        Future<T> future = taskManager.submit(callable);
        monitorTimeout(future, timeout, timeUnit);
        return future;
    }

    public <T> Future<T> submit(@NonNull SqlExecuteCallable<T> callable,
            long timeout, TimeUnit timeUnit, @NonNull SqlExecuteTaskManager taskManager) {
        Future<T> future = taskManager.submit(callable);
        monitorTimeout(future, timeout, timeUnit);
        return future;
    }

    private void monitorTimeout(Future<?> target, long timeout, TimeUnit timeUnit) {
        TaskTimeoutInfo timeoutInfo = new TaskTimeoutInfo(target, timeout, timeUnit);
        boolean result = delayQueue.offer(timeoutInfo);
        log.info("Task execution has been added to timeout monitoring, expiredTime={}, operateResult={}",
                new Date(timeoutInfo.expiredTime), result);
    }

    /**
     * Task timeout monitoring task
     *
     * @author yh263208
     * @date 2021-11-11 21:31
     * @since ODC_release_3.2.2
     */
    @Slf4j
    static class TimeoutMonitorTask implements Runnable {

        private final DelayQueue<TaskTimeoutInfo> delayQueue;

        public TimeoutMonitorTask(@NonNull DelayQueue<TaskTimeoutInfo> delayQueue) {
            this.delayQueue = delayQueue;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    doRun();
                } catch (InterruptedException e) {
                    log.warn("Task timeout monitoring task is interrupted, task exit", e);
                    return;
                } catch (Exception e1) {
                    log.warn("Task timeout monitoring task is abnormal, the task is restarted", e1);
                }
            }
        }

        private void doRun() throws InterruptedException {
            TaskTimeoutInfo info = delayQueue.take();
            log.info("Asynchronous task has timed out, expiredTime={}", new Date(info.getExpiredTime()));
            if (info.getTarget().isDone() || info.getTarget().isCancelled()) {
                return;
            }
            boolean result = info.getTarget().cancel(true);
            log.info("Asynchronous task has been interrupted, result={}. expiredTime={}", result,
                    new Date(info.getExpiredTime()));
        }
    }

    /**
     * Used to encapsulate timeout-related information for asynchronous tasks with timeout settings
     *
     * @author yh263208
     * @date 2021-11-11 21:29
     * @since ODC_release_3.2.2
     */
    @Getter
    static class TaskTimeoutInfo implements Delayed {
        public final Future<?> target;
        public final long expiredTime;

        public TaskTimeoutInfo(Future<?> target, long timeout, TimeUnit timeUnit) {
            this.target = target;
            this.expiredTime = System.currentTimeMillis() + TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(expiredTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            if (!(o instanceof TaskTimeoutInfo)) {
                return 1;
            }
            return (int) (this.getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS));
        }
    }

}
