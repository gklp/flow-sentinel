package com.flowsentinel.starter.config;

import com.flowsentinel.core.definition.FlowDefinitionProvider;
import com.flowsentinel.core.engine.FlowEngine;
import com.flowsentinel.core.parser.FlowDefinitionParser;
import com.flowsentinel.starter.web.FlowDefinitionRegistry;
import com.flowsentinel.starter.web.FlowGuardAspect;
import com.flowsentinel.starter.web.FlowStateArgumentResolver;
import com.flowsentinel.starter.web.provider.DefaultFlowIdProvider;
import com.flowsentinel.core.provider.FlowIdProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@AutoConfiguration
public class FlowWebAutoConfiguration implements WebMvcConfigurer {

    private final FlowEngine flowEngine;
    private final FlowIdProvider flowIdProvider;

    // Constructor injection for beans that will be used in the resolver
    public FlowWebAutoConfiguration(FlowEngine flowEngine, FlowIdProvider flowIdProvider) {
        this.flowEngine = flowEngine;
        this.flowIdProvider = flowIdProvider;
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
                                           FlowDefinitionProvider definitionProvider,
                                           FlowIdProvider flowIdProvider) {
        return new FlowGuardAspect(flowEngine, definitionProvider, flowIdProvider);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new FlowStateArgumentResolver(flowEngine, flowIdProvider));
    }
}