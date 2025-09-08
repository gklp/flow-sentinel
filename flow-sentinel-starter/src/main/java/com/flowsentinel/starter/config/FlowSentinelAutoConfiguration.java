package com.flowsentinel.starter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsentinel.core.definition.FlowDefinitionProvider;
import com.flowsentinel.core.engine.DefaultFlowEngine;
import com.flowsentinel.core.engine.FlowEngine;
import com.flowsentinel.core.parser.FlowDefinitionParser;
import com.flowsentinel.core.store.FlowStore;
import com.flowsentinel.starter.web.FlowDefinitionRegistry;
import com.flowsentinel.starter.web.FlowGuardAspect;
import com.flowsentinel.starter.web.provider.DefaultFlowIdProvider;
import com.flowsentinel.core.provider.FlowIdProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnBean(FlowStore.class)
@AutoConfigureAfter(name = {
        "com.flowsentinel.store.redis.config.RedisStorageAutoConfiguration",
        "com.flowsentinel.store.inmemory.config.InMemoryStorageAutoConfiguration"
})
public class FlowSentinelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FlowEngine flowEngine(FlowStore flowStore, FlowDefinitionProvider flowDefinitionProvider) {
        return new DefaultFlowEngine(flowStore, flowDefinitionProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowDefinitionParser flowDefinitionParser(final ObjectMapper objectMapper) {
        return new FlowDefinitionParser(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowDefinitionRegistry flowDefinitionRegistry(FlowDefinitionParser parser) {
        return new FlowDefinitionRegistry(parser);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowIdProvider flowIdProvider() {
        return new DefaultFlowIdProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowGuardAspect flowGuardAspect(FlowEngine flowEngine,
                                           FlowDefinitionRegistry registry,
                                           FlowIdProvider flowIdProvider) {
        return new FlowGuardAspect(flowEngine, registry, flowIdProvider);
    }

}