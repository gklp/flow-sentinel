package com.flowsentinel.starter.web;

import com.flowsentinel.core.definition.FlowDefinition;
import com.flowsentinel.core.definition.FlowDefinitionProvider;
import com.flowsentinel.core.engine.FlowEngine;
import com.flowsentinel.core.id.FlowContext;
import com.flowsentinel.core.id.FlowKey;
import com.flowsentinel.starter.web.annotation.Flow;
import com.flowsentinel.starter.web.annotation.FlowStep;
import com.flowsentinel.starter.web.provider.FlowIdProvider;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Aspect
@Component
public class FlowGuardAspect {

	private final FlowEngine flowEngine;
	private final FlowDefinitionProvider definitionProvider;
	private final FlowIdProvider flowIdProvider;

	public FlowGuardAspect(FlowEngine flowEngine,
	                       FlowDefinitionProvider definitionProvider,
	                       FlowIdProvider flowIdProvider) {
		this.flowEngine = flowEngine;
		this.definitionProvider = definitionProvider;
		this.flowIdProvider = flowIdProvider;
	}

	@Around("@within(flowAnnotation) && @annotation(flowStep)")
	public Object aroundFlowStep(ProceedingJoinPoint joinPoint, Flow flowAnnotation, FlowStep flowStep) throws Throwable {
		FlowContext flowContext = flowIdProvider.provide();
		FlowKey flowKey = new FlowKey(flowAnnotation.name(), flowContext.ownerId(), flowContext.flowId());

		FlowDefinition definition = definitionProvider.getDefinition(flowAnnotation.name())
				.orElseThrow(() -> new IllegalStateException("Flow definition not found: " + flowAnnotation.name()));

		Map<String, Object> payload = extractPayloadMap(joinPoint);

		// 1) Preview only (no persist)
		com.flowsentinel.core.runtime.FlowState planned;
		if (flowStep.start()) {
			planned = flowEngine.previewStart(flowKey, definition, payload);
		} else {
			planned = flowEngine.previewAdvance(flowKey, definition, payload);
		}

		// 2) Execute controller
		Object result = joinPoint.proceed();

		// 3) Persist if successful
		flowEngine.persist(flowKey, planned);

		return result;
	}

	private Map<String, Object> extractPayloadMap(ProceedingJoinPoint joinPoint) {
		Map<String, Object> payload = new HashMap<>();

		Optional<Object> requestBodyOpt = extractRequestBodyPayload(joinPoint);
		if (requestBodyOpt.isPresent()) {
			Object requestBodyValue = requestBodyOpt.get();

			if (requestBodyValue instanceof Map<?, ?> map) {
				map.forEach((k, v) -> {
					if (k instanceof String key) {
						payload.put(key, v);
					}
				});
			} else {
				Optional<String> paramName = getRequestBodyArgumentName(joinPoint);
				String key = paramName.orElse("requestBody");
				payload.put(key, requestBodyValue);
			}
		}

		return payload;
	}

	private Optional<Object> extractRequestBodyPayload(ProceedingJoinPoint joinPoint) {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		Method method = signature.getMethod();
		Annotation[][] parameterAnnotations = method.getParameterAnnotations();
		Object[] args = joinPoint.getArgs();

		for (int i = 0; i < parameterAnnotations.length; i++) {
			for (Annotation annotation : parameterAnnotations[i]) {
				if (annotation instanceof RequestBody) {
					return Optional.ofNullable(args[i]);
				}
			}
		}
		return Optional.empty();
	}

	private Optional<String> getRequestBodyArgumentName(ProceedingJoinPoint joinPoint) {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		String[] parameterNames = signature.getParameterNames();
		Annotation[][] parameterAnnotations = signature.getMethod().getParameterAnnotations();

		for (int i = 0; i < parameterAnnotations.length; i++) {
			for (Annotation annotation : parameterAnnotations[i]) {
				if (annotation instanceof RequestBody) {
					return Optional.of(parameterNames[i]);
				}
			}
		}
		return Optional.empty();
	}
}