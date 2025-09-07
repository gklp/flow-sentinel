package com.flowsentinel.starter.config;

import com.flowsentinel.core.definition.FlowDefinitionProvider;
import com.flowsentinel.core.engine.DefaultFlowEngine;
import com.flowsentinel.core.engine.FlowEngine;
import com.flowsentinel.core.parser.FlowDefinitionParser;
import com.flowsentinel.core.runner.FlowRunner;
import com.flowsentinel.core.store.FlowStore;
import com.flowsentinel.starter.web.FlowDefinitionRegistry;
import com.flowsentinel.starter.web.FlowGuardAspect;
import com.flowsentinel.starter.web.provider.DefaultFlowIdProvider;
import com.flowsentinel.starter.web.provider.FlowIdProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Main auto-configuration for FlowSentinel.
 * <p>
 * This class provides the core beans like {@link FlowEngine} and {@link FlowRunner},
 * and imports the storage-specific auto-configurations. The user can then choose
 * a storage implementation (e.g., in-memory or Redis) via properties and dependencies.
 */
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
    public FlowRunner flowRunner(FlowEngine flowEngine, FlowDefinitionParser flowDefinitionParser) {
        return new FlowRunner(flowEngine, flowDefinitionParser);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowDefinitionParser flowDefinitionParser() {
        return new FlowDefinitionParser();
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