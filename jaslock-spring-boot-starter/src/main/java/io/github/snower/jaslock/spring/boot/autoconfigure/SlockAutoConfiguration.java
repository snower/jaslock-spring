package io.github.snower.jaslock.spring.boot.autoconfigure;

import io.github.snower.jaslock.exceptions.ClientUnconnectException;
import io.github.snower.jaslock.spring.boot.SlockTemplate;
import io.github.snower.jaslock.spring.boot.aspects.IdempotentAspect;
import io.github.snower.jaslock.spring.boot.aspects.LockAspect;
import io.github.snower.jaslock.spring.boot.aspects.MaxConcurrentFlowAspect;
import io.github.snower.jaslock.spring.boot.aspects.TokenBucketFlowAspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@EnableConfigurationProperties(SlockProperties.class)
@ConditionalOnProperty(value = "spring.slock.enabled", havingValue = "true", matchIfMissing = true)
public class SlockAutoConfiguration {
    @ConditionalOnMissingBean
    @Bean(value = "slockTemplate", destroyMethod = "close")
    public SlockTemplate slockTemplate(SlockProperties slockProperties) {
        SlockTemplate slockTemplate = new SlockTemplate(slockProperties.buildConfiguration());
        try {
            slockTemplate.open();
        } catch (ClientUnconnectException | IOException e) {
            throw new IllegalStateException(e);
        }
        return slockTemplate;
    }

    @ConditionalOnBean(SlockTemplate.class)
    @ConditionalOnMissingBean
    @Bean
    public LockAspect lockAspect(SlockTemplate slockTemplate) {
        return new LockAspect(slockTemplate);
    }

    @ConditionalOnBean(SlockTemplate.class)
    @ConditionalOnMissingBean
    @Bean
    public MaxConcurrentFlowAspect maxConcurrentFlowAspect(SlockTemplate slockTemplate) {
        return new MaxConcurrentFlowAspect(slockTemplate);
    }

    @ConditionalOnBean(SlockTemplate.class)
    @ConditionalOnMissingBean
    @Bean
    public TokenBucketFlowAspect tokenBucketFlowAspect(SlockTemplate slockTemplate) {
        return new TokenBucketFlowAspect(slockTemplate);
    }

    @ConditionalOnBean(SlockTemplate.class)
    @ConditionalOnMissingBean
    @Bean
    public IdempotentAspect idempotentAspect(SlockTemplate slockTemplate) {
        return new IdempotentAspect(slockTemplate);
    }
}
