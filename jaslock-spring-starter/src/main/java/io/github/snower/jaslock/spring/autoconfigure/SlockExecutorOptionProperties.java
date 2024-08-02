package io.github.snower.jaslock.spring.autoconfigure;

import io.github.snower.jaslock.callback.ExecutorOption;

import java.util.concurrent.TimeUnit;

public class SlockExecutorOptionProperties {
    private Integer workerCount = 1;
    private Integer maxWorkerCount = 2;
    private Integer maxCapacity = Integer.MAX_VALUE;
    private Integer workerKeepAliveTime = 7200;
    private TimeUnit workerKeepAliveTimeUnit = TimeUnit.SECONDS;

    public void setWorkerCount(int workerCount) {
        this.workerCount = workerCount;
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public void setMaxWorkerCount(int maxWorkerCount) {
        this.maxWorkerCount = maxWorkerCount;
    }

    public int getMaxWorkerCount() {
        return maxWorkerCount;
    }

    public void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void setWorkerKeepAliveTime(int workerKeepAliveTime) {
        this.workerKeepAliveTime = workerKeepAliveTime;
    }

    public int getWorkerKeepAliveTime() {
        return workerKeepAliveTime;
    }

    public void setWorkerKeepAliveTimeUnit(TimeUnit workerKeepAliveTimeUnit) {
        this.workerKeepAliveTimeUnit = workerKeepAliveTimeUnit;
    }

    public TimeUnit getWorkerKeepAliveTimeUnit() {
        return workerKeepAliveTimeUnit;
    }

    public ExecutorOption buildExecutorOption() {
        return new ExecutorOption(workerCount, maxWorkerCount, maxCapacity, workerKeepAliveTime, workerKeepAliveTimeUnit);
    }
}
