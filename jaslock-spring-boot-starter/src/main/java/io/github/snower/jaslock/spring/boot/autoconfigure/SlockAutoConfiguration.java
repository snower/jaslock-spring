package io.github.snower.jaslock.spring.boot.autoconfigure;

import io.github.snower.jaslock.exceptions.ClientUnconnectException;
import io.github.snower.jaslock.spring.boot.SlockSerializater;
import io.github.snower.jaslock.spring.boot.SlockTemplate;
import io.github.snower.jaslock.spring.boot.aspects.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;

@Configuration
@EnableConfigurationProperties(SlockProperties.class)
@ConditionalOnProperty(value = "spring.slock.enabled", havingValue = "true", matchIfMissing = true)
public class SlockAutoConfiguration {
    @ConditionalOnMissingBean
    @Bean(value = "slockSerializater")
    public SlockSerializater slockSerializater() {
        return new SlockSerializater.ObjectSerializater();
    }

    @ConditionalOnMissingBean(value = SlockTemplate.class, name = "slockTemplate")
    @Bean(value = "slockTemplate", destroyMethod = "close")
    public SlockTemplate slockTemplate(SlockProperties slockProperties, SlockSerializater slockSerializater) {
        SlockTemplate slockTemplate = new SlockTemplate(slockProperties.buildConfiguration(), slockSerializater);
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

    @ConditionalOnClass(TransactionSynchronizationManager.class)
    @ConditionalOnBean(SlockTemplate.class)
    @ConditionalOnMissingBean
    @Bean
    public LockWithTransactionAspect lockWithTransactionAspect(SlockTemplate slockTemplate) {
        return new LockWithTransactionAspect(slockTemplate);
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
    public IdempotentAspect idempotentAspect(SlockTemplate slockTemplate, SlockSerializater slockSerializater) {
        return new IdempotentAspect(slockTemplate, slockSerializater);
    }

    @ConditionalOnBean(SlockTemplate.class)
    @ConditionalOnMissingBean
    @Bean
    public LocksAspect locksAspect(SlockTemplate slockTemplate) {
        return new LocksAspect(slockTemplate);
    }

    @ConditionalOnClass(TransactionSynchronizationManager.class)
    @ConditionalOnBean(SlockTemplate.class)
    @ConditionalOnMissingBean
    @Bean
    public LockWithTransactionsAspect lockWithTransactionsAspect(SlockTemplate slockTemplate) {
        return new LockWithTransactionsAspect(slockTemplate);
    }

    @ConditionalOnBean(SlockTemplate.class)
    @ConditionalOnMissingBean
    @Bean
    public MaxConcurrentFlowsAspect maxConcurrentFlowsAspect(SlockTemplate slockTemplate) {
        return new MaxConcurrentFlowsAspect(slockTemplate);
    }

    @ConditionalOnBean(SlockTemplate.class)
    @ConditionalOnMissingBean
    @Bean
    public TokenBucketFlowsAspect tokenBucketFlowsAspect(SlockTemplate slockTemplate) {
        return new TokenBucketFlowsAspect(slockTemplate);
    }
}
