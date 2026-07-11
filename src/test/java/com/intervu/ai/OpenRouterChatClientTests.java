package com.intervu.ai;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenRouterChatClientTests {

	@Test
	void chatReturnsContentFromFirstChoice() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		Environment environment = Mockito.mock(Environment.class);
		when(environment.getProperty("openrouter.url", "https://openrouter.ai/api/v1/chat/completions")).thenReturn("https://openrouter.ai/api/v1");
		when(environment.getProperty("openrouter.api.key", "")).thenReturn("sk-test-key");
		when(environment.getProperty("openrouter.model", "google/gemini-2.5-flash")).thenReturn("test-model");

		RestClient.RequestBodyUriSpec requestUriSpec = Mockito.mock(RestClient.RequestBodyUriSpec.class);
		RestClient.RequestBodySpec requestBodySpec = Mockito.mock(RestClient.RequestBodySpec.class);
		RestClient.ResponseSpec responseSpec = Mockito.mock(RestClient.ResponseSpec.class);

		RestClient restClient = Mockito.mock(RestClient.class);
		when(restClient.post()).thenReturn(requestUriSpec);
		when(requestUriSpec.uri(any(URI.class))).thenReturn(requestBodySpec);
		when(requestBodySpec.body(any(String.class))).thenReturn(requestBodySpec);
		when(requestBodySpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.body(String.class)).thenReturn("{\"choices\":[{\"message\":{\"content\":\"Hello from LLM\"}}]}");

		OpenRouterChatClient client = new OpenRouterChatClient(objectMapper, environment, restClient);

		String result = client.chat("You are an evaluator", "Evaluate this answer");
		assertThat(result).isEqualTo("Hello from LLM");
	}

	@Test
	void chatThrowsOnEmptyChoices() {
		ObjectMapper objectMapper = new ObjectMapper();
		Environment environment = Mockito.mock(Environment.class);
		when(environment.getProperty("openrouter.url", "https://openrouter.ai/api/v1/chat/completions")).thenReturn("https://openrouter.ai/api/v1");
		when(environment.getProperty("openrouter.api.key", "")).thenReturn("sk-test-key");
		when(environment.getProperty("openrouter.model", "google/gemini-2.5-flash")).thenReturn("test-model");

		RestClient.RequestBodyUriSpec requestUriSpec = Mockito.mock(RestClient.RequestBodyUriSpec.class);
		RestClient.RequestBodySpec requestBodySpec = Mockito.mock(RestClient.RequestBodySpec.class);
		RestClient.ResponseSpec responseSpec = Mockito.mock(RestClient.ResponseSpec.class);

		RestClient restClient = Mockito.mock(RestClient.class);
		when(restClient.post()).thenReturn(requestUriSpec);
		when(requestUriSpec.uri(any(URI.class))).thenReturn(requestBodySpec);
		when(requestBodySpec.body(any(String.class))).thenReturn(requestBodySpec);
		when(requestBodySpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.body(String.class)).thenReturn("{\"choices\":[]}");

		OpenRouterChatClient client = new OpenRouterChatClient(objectMapper, environment, restClient);

		assertThatThrownBy(() -> client.chat("system", "user"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("no choices");
	}

	@Test
	void postsToConfiguredFullEndpointUrl() {
		ObjectMapper objectMapper = new ObjectMapper();
		Environment environment = Mockito.mock(Environment.class);
		when(environment.getProperty("openrouter.url", "https://openrouter.ai/api/v1/chat/completions")).thenReturn("https://openrouter.ai/api/v1/chat/completions");
		when(environment.getProperty("openrouter.api.key", "")).thenReturn("sk-test-key");
		when(environment.getProperty("openrouter.model", "google/gemini-2.5-flash")).thenReturn("test-model");

		RestClient.RequestBodyUriSpec requestUriSpec = Mockito.mock(RestClient.RequestBodyUriSpec.class);
		RestClient.RequestBodySpec requestBodySpec = Mockito.mock(RestClient.RequestBodySpec.class);
		RestClient.ResponseSpec responseSpec = Mockito.mock(RestClient.ResponseSpec.class);

		RestClient restClient = Mockito.mock(RestClient.class);
		when(restClient.post()).thenReturn(requestUriSpec);
		when(requestUriSpec.uri(any(URI.class))).thenReturn(requestBodySpec);
		when(requestBodySpec.body(any(String.class))).thenReturn(requestBodySpec);
		when(requestBodySpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.body(String.class)).thenReturn("{\"choices\":[{\"message\":{\"content\":\"Hello from LLM\"}}]}");

		OpenRouterChatClient client = new OpenRouterChatClient(objectMapper, environment, restClient);

		client.chat("You are an evaluator", "Evaluate this answer");

		ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
		verify(requestUriSpec).uri(uriCaptor.capture());
		assertThat(uriCaptor.getValue()).isEqualTo(URI.create("https://openrouter.ai/api/v1/chat/completions"));
	}
}
