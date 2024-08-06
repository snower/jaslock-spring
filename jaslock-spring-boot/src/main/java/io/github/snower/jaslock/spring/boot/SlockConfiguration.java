package io.github.snower.jaslock.spring.boot;

import io.github.snower.jaslock.callback.ExecutorOption;

import java.util.ArrayList;
import java.util.List;

public class SlockConfiguration {
    private final String host;
    private final int port;
    private final List<String> hosts;
    private final int databaseId;
    private final ExecutorOption executorOption;
    private final short defaultTimeoutFlag;
    private final short defaultExpriedFlag;

    public SlockConfiguration(String host, Integer port, List<String> hosts, int databaseId, ExecutorOption executorOption,
                              short defaultTimeoutFlag, short defaultExpriedFlag) {
        this.host = host;
        this.port = port;
        this.hosts = hosts;
        this.databaseId = databaseId;
        this.executorOption = executorOption;
        this.defaultTimeoutFlag = defaultTimeoutFlag;
        this.defaultExpriedFlag = defaultExpriedFlag;
    }

    public String getHost() {
        return host == null || host.isEmpty() ? "127.0.0.1" : host;
    }

    public int getPort() {
        return port <= 0 ? 5658 : port;
    }

    public List<String> getHosts() {
        return hosts;
    }

    public int getDatabaseId() {
        return databaseId < 0 || databaseId > 127 ? 0 : databaseId;
    }

    public ExecutorOption getExecutorOption() {
        return executorOption;
    }

    public short getDefaultTimeoutFlag() {
        return defaultTimeoutFlag;
    }

    public short getDefaultExpriedFlag() {
        return defaultExpriedFlag;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private String host;
        private int port;
        private List<String> hosts;
        private int databaseId = 0;
        private ExecutorOption executorOption;
        private short defaultTimeoutFlag;
        private short defaultExpriedFlag;

        public Builder setHost(String host) {
            this.host = host;
            return this;
        }

        public Builder setPort(int port) {
            this.port = port;
            return this;
        }

        public Builder setHosts(List<String> hosts) {
            this.hosts = hosts;
            return this;
        }

        public Builder addHost(String host) {
            if (this.hosts == null) {
                this.hosts = new ArrayList<>();
            }
            this.hosts.add(host);
            return this;
        }

        public Builder setDatabaseId(int databaseId) {
            this.databaseId = databaseId;
            return this;
        }

        public int getDatabaseId() {
            return databaseId;
        }

        public Builder setExecutorOption(ExecutorOption executorOption) {
            this.executorOption = executorOption;
            return this;
        }

        public Builder setDefaultTimeoutFlag(short defaultTimeoutFlag) {
            this.defaultTimeoutFlag = defaultTimeoutFlag;
            return this;
        }

        public Builder setDefaultExpriedFlag(short defaultExpriedFlag) {
            this.defaultExpriedFlag = defaultExpriedFlag;
            return this;
        }

        public SlockConfiguration build() {
            return new SlockConfiguration(host, port, hosts, databaseId, executorOption, defaultTimeoutFlag, defaultExpriedFlag);
        }
    }
}
