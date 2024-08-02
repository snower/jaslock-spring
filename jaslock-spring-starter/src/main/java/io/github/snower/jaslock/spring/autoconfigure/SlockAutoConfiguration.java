package io.github.snower.jaslock.spring.autoconfigure;

import io.github.snower.jaslock.spring.SlockTemplate;
import io.github.snower.jaslock.spring.aspects.LockAspect;
import io.github.snower.jaslock.spring.aspects.MaxConcurrentFlowAspect;
import io.github.snower.jaslock.spring.aspects.TokenBucketFlowAspect;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
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
    @Bean(value = "slockTemplate", destroyMethod = "close")
    public SlockTemplate slockTemplate(SlockProperties slockProperties) {
        return new SlockTemplate(slockProperties.buildConfiguration());
    }

    @ConditionalOnClass(Aspect.class)
    @ConditionalOnBean(SlockTemplate.class)
    @ConditionalOnMissingBean
    @Bean
    public LockAspect lockAspect(SlockTemplate slockTemplate) {
        return new LockAspect(slockTemplate);
    }

    @ConditionalOnClass(Aspect.class)
    @ConditionalOnBean(SlockTemplate.class)
    @ConditionalOnMissingBean
    @Bean
    public MaxConcurrentFlowAspect maxConcurrentFlowAspect(SlockTemplate slockTemplate) {
        return new MaxConcurrentFlowAspect(slockTemplate);
    }

    @ConditionalOnClass(Aspect.class)
    @ConditionalOnBean(SlockTemplate.class)
    @ConditionalOnMissingBean
    @Bean
    public TokenBucketFlowAspect tokenBucketFlowAspect(SlockTemplate slockTemplate) {
        return new TokenBucketFlowAspect(slockTemplate);
    }
}
