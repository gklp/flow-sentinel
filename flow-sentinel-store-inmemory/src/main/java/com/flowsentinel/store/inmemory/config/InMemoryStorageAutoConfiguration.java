package com.flowsentinel.store.inmemory.config;

import com.flowsentinel.core.store.FlowStore;
import com.flowsentinel.store.inmemory.core.InMemoryFlowStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(FlowSentinelInMemoryProperties.class)
public class InMemoryStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FlowStore.class)
    public FlowStore inMemoryFlowStore(FlowSentinelInMemoryProperties properties) {
        return new InMemoryFlowStore(properties);
    }

}