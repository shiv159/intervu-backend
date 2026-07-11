package com.intervu.questionbank.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

@Component
public class OpenRouterEmbeddingClient implements EmbeddingClient {

	private static final int DEFAULT_DIMENSIONS = 1536;

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final URI endpoint;
	private final String apiKey;
	private final String model;
	private final int maxTokens;

	public OpenRouterEmbeddingClient(
		ObjectMapper objectMapper,
		@Value("${openrouter.embedding.url}") String endpoint,
		@Value("${openrouter.api.key}") String apiKey,
		@Value("${openrouter.embedding.model}") String model,
		@Value("${openrouter.embedding.max-tokens:512}") int maxTokens
	) {
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
		this.objectMapper = objectMapper;
		this.endpoint = URI.create(endpoint);
		this.apiKey = apiKey;
		this.model = model;
		this.maxTokens = Math.max(1, maxTokens);
	}

	@Override
	public EmbeddingResult embed(String input) {
		if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("mock_key")) {
			return new EmbeddingResult(model, mockEmbedding(input));
		}

		try {
			String boundedInput = limitTokens(input);
			String body = objectMapper.writeValueAsString(Map.of(
				"input", boundedInput,
				"model", model,
				"dimensions", DEFAULT_DIMENSIONS,
				"encoding_format", "float"
			));
			HttpRequest request = HttpRequest.newBuilder(endpoint)
				.timeout(Duration.ofSeconds(30))
				.header("Authorization", "Bearer " + apiKey)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException(describeFailure(response.statusCode(), response.body()));
			}
			OpenRouterEmbeddingsResponse parsed = objectMapper.readValue(response.body(), OpenRouterEmbeddingsResponse.class);
			if (parsed.data() == null || parsed.data().isEmpty()) {
				throw new IllegalStateException("OpenRouter embeddings response contained no vectors");
			}
			return new EmbeddingResult(parsed.model() == null ? model : parsed.model(), parsed.data().getFirst().embedding());
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to call OpenRouter embeddings API", ex);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("OpenRouter embeddings request interrupted", ex);
		}
	}

	private String limitTokens(String input) {
		if (input == null || input.isBlank()) {
			return "";
		}
		String[] tokens = input.trim().split("\\s+");
		if (tokens.length <= maxTokens) {
			return input;
		}
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < maxTokens; i++) {
			if (i > 0) {
				builder.append(' ');
			}
			builder.append(tokens[i]);
		}
		return builder.toString();
	}

	private String shorten(String body) {
		if (body == null || body.length() <= 512) {
			return body;
		}
		return body.substring(0, 512) + "...";
	}

	private String describeFailure(int statusCode, String body) {
		String suffix = shorten(body);
		if (statusCode == 402) {
			return "OpenRouter embeddings quota exhausted (402 Payment Required): " + suffix;
		}
		return "OpenRouter embeddings request failed with status " + statusCode + ": " + suffix;
	}

	public record OpenRouterEmbeddingsResponse(List<EmbeddingDatum> data, String model) {
	}

	public record EmbeddingDatum(List<Double> embedding, String object, int index) {
	}

	private List<Double> mockEmbedding(String input) {
		long seed = seedFrom(input);
		SplittableRandom random = new SplittableRandom(seed);
		List<Double> embedding = new ArrayList<>(DEFAULT_DIMENSIONS);
		for (int i = 0; i < DEFAULT_DIMENSIONS; i++) {
			embedding.add(random.nextDouble(-1.0, 1.0));
		}
		return embedding;
	}

	private long seedFrom(String input) {
		long seed = 0L;
		if (input != null) {
			for (char ch : input.toCharArray()) {
				seed = (seed * 31) + ch;
			}
		}
		return seed;
	}
}
