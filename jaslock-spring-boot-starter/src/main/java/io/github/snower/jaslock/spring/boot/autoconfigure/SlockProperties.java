package io.github.snower.jaslock.spring.boot.autoconfigure;

import io.github.snower.jaslock.callback.ExecutorOption;
import io.github.snower.jaslock.spring.boot.SlockConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "spring.slock")
@ConditionalOnProperty(value = "spring.slock.enabled", havingValue = "true", matchIfMissing = true)
public class SlockProperties {
    private boolean enabled = true;
    private String url = null;
    private String host = "127.0.0.1";
    private Integer port = 5658;
    private List<String> hosts = null;
    private Integer databaseId = 0;
    private SlockExecutorOptionProperties executor = null;
    private Short defaultTimeoutFlag = 0;
    private Short defaultExpriedFlag = 0;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    public void setHosts(List<String> hosts) {
        this.hosts = hosts;
    }

    public List<String> getHosts() {
        return hosts;
    }

    public void setDatabaseId(Integer databaseId) {
        this.databaseId = databaseId;
    }

    public Integer getDatabaseId() {
        return databaseId;
    }

    public void setExecutor(SlockExecutorOptionProperties executor) {
        this.executor = executor;
    }

    public SlockExecutorOptionProperties getExecutor() {
        return executor;
    }

    public void setDefaultTimeoutFlag(Short defaultTimeoutFlag) {
        this.defaultTimeoutFlag = defaultTimeoutFlag;
    }

    public Short getDefaultTimeoutFlag() {
        return defaultTimeoutFlag;
    }

    public void setDefaultExpriedFlag(Short defaultExpriedFlag) {
        this.defaultExpriedFlag = defaultExpriedFlag;
    }

    public Short getDefaultExpriedFlag() {
        return defaultExpriedFlag;
    }

    public SlockConfiguration buildConfiguration() {
        if (url != null && !url.isEmpty()) {
            if (!url.startsWith("slock://")) throw new IllegalArgumentException("slock unknown url schema");

            try {
                URI uri = new URI(url);
                List<String> hosts = Arrays.stream(uri.getAuthority().split(",")).map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                String queryString = uri.getQuery();
                Map<String, String> params = queryString == null || queryString.isEmpty() ? new HashMap<>() :
                        Arrays.stream(uri.getQuery().split("&")).map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .map(it -> {
                                    final int idx = it.indexOf("=");
                                    return new AbstractMap.SimpleImmutableEntry<>((idx > 0 ? it.substring(0, idx) : it).trim(),
                                            idx > 0 && it.length() > idx + 1 ? (it.substring(idx + 1)).trim() : null);
                                })
                                .collect(Collectors.toMap(AbstractMap.SimpleImmutableEntry::getKey,
                                        AbstractMap.SimpleImmutableEntry::getValue, (a, b) -> b));
                ExecutorOption executorOption = null;
                if (params.keySet().stream().anyMatch(k -> k.startsWith("executor"))) {
                    executorOption = new ExecutorOption(Integer.parseInt(params.getOrDefault("executorWorkerCount", "1")),
                            Integer.parseInt(params.getOrDefault("executorMaxWorkerCount", "2")),
                            Integer.parseInt(params.getOrDefault("executorMaxCapacity", "2147483647")),
                            Integer.parseInt(params.getOrDefault("executorWorkerKeepAliveTime", "7200")),
                            TimeUnit.SECONDS);
                }
                if (hosts.size() > 1) {
                    return new SlockConfiguration(null, 0, hosts,
                            Integer.parseInt(params.getOrDefault("database", "0")), executorOption,
                            Short.parseShort(params.getOrDefault("defaultTimeoutFlag", "0")),
                            Short.parseShort(params.getOrDefault("defaultExpriedFlag", "0")));
                }
                return new SlockConfiguration(uri.getHost() == null ? "127.0.0.1" : uri.getHost(), uri.getPort(), null,
                        Integer.parseInt(params.getOrDefault("database", "0")), executorOption,
                        Short.parseShort(params.getOrDefault("defaultTimeoutFlag", "0")),
                        Short.parseShort(params.getOrDefault("defaultExpriedFlag", "0")));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return new SlockConfiguration(host, port == null ? 0 : port, hosts,
                databaseId == null ? 0 : databaseId,
                executor != null ? executor.buildExecutorOption() : null,
                defaultTimeoutFlag, defaultExpriedFlag);
    }
}
