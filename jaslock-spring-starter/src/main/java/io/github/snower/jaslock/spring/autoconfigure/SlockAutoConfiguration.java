package io.github.snower.jaslock.spring.autoconfigure;

import io.github.snower.jaslock.spring.SlockTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SlockProperties.class)
@ConditionalOnProperty(value = "spring.slock.enabled", havingValue = "true", matchIfMissing = true)
public class SlockAutoConfiguration {
    @ConditionalOnMissingBean
    @Bean
    public SlockTemplate slockTemplate(SlockProperties slockProperties) {
        return new SlockTemplate(slockProperties.buildConfiguration());
    }
}
