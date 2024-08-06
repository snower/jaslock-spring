package io.github.snower.jaslock.spring.boot.autoconfigure;

import io.github.snower.jaslock.spring.boot.actuate.SlockHealthIndicator;
import io.github.snower.jaslock.spring.boot.SlockTemplate;
import org.springframework.boot.actuate.autoconfigure.OnEndpointElementCondition;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(OnEndpointElementCondition.class)
@ConditionalOnProperty(value = "spring.slock.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(SlockAutoConfiguration.class)
public class SlockAutoActuateConfiguration {
    @ConditionalOnProperty(value = "management.health.slock.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean(SlockTemplate.class)
    @ConditionalOnMissingBean
    @Bean
    public SlockHealthIndicator slockHealthIndicator(SlockTemplate slockTemplate) {
        return new SlockHealthIndicator(slockTemplate);
    }
}
