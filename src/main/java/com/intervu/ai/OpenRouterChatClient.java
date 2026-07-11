package com.intervu.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Component
public class OpenRouterChatClient {

	private static final String DEFAULT_CHAT_URL = "https://openrouter.ai/api/v1/chat/completions";

	private final RestClient restClient;
	private final String model;
	private final String chatUrl;
	private final ObjectMapper objectMapper;

	@Autowired
	public OpenRouterChatClient(
		ObjectMapper objectMapper,
		Environment environment
	) {
		this(objectMapper, environment, buildRestClient(environment));
	}

	OpenRouterChatClient(ObjectMapper objectMapper, Environment environment, RestClient restClient) {
		this.objectMapper = objectMapper;
		this.restClient = restClient;
		this.model = environment.getProperty("openrouter.model", "google/gemini-2.5-flash");
		this.chatUrl = resolveChatUrl(environment);
	}

	private static String resolveChatUrl(Environment environment) {
		String url = environment.getProperty("openrouter.url", DEFAULT_CHAT_URL);
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	private static RestClient buildRestClient(Environment environment) {
		String apiKey = environment.getProperty("openrouter.api.key", "");
		return RestClient.builder()
			.defaultHeader("Authorization", "Bearer " + apiKey)
			.defaultHeader("Content-Type", "application/json")
			.build();
	}

	public String chat(String systemPrompt, String userPrompt) {
		String effectiveSystem = systemPrompt == null || systemPrompt.isBlank() ? "You are a helpful interview evaluator." : systemPrompt;
		String effectiveUser = userPrompt == null ? "" : userPrompt;

		Map<String, Object> body = Map.of(
			"model", model,
			"messages", List.of(
				Map.of("role", "system", "content", effectiveSystem),
				Map.of("role", "user", "content", effectiveUser)
			),
			"max_tokens", 1024,
			"temperature", 0
		);

		try {
			String bodyJson = objectMapper.writeValueAsString(body);
			String responseJson = restClient.post()
				.uri(URI.create(chatUrl))
			.body(bodyJson)
			.retrieve()
			.body(String.class);

		ChatResponse response = objectMapper.readValue(responseJson, ChatResponse.class);
		if (response.choices() == null || response.choices().isEmpty()) {
			throw new IllegalStateException("OpenRouter response contained no choices");
		}
		return response.choices().getFirst().message().content();
	} catch (RestClientException ex) {
		throw new RuntimeException("OpenRouter chat request failed: " + ex.getMessage(), ex);
	}
	}

	public String model() {
		return model;
	}

	public record ChatResponse(List<Choice> choices) {
	}

	public record Choice(Message message) {
	}

	public record Message(String content) {
	}
}
