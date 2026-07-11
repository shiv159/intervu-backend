package com.intervu;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
class HealthController {

	@GetMapping("/api/health")
	Map<String, String> health() {
		return Map.of("status", "ok");
	}

	@GetMapping("/api/health/ai")
	Map<String, Object> aiHealth(
		@Value("${intervu.ai.mode:MOCK}") String aiMode,
		@Value("${openrouter.model:google/gemini-2.5-flash}") String model,
		@Value("${openrouter.embedding.model:openai/text-embedding-3-small}") String embeddingModel,
		@Value("${openrouter.api.key:mock_key_for_build}") String apiKey
	) {
		String keyStatus = (apiKey == null || apiKey.isBlank() || apiKey.startsWith("mock_key")) ? "no-key" : "ok";
		String llm;
		String embedding;
		if ("LIVE".equalsIgnoreCase(aiMode)) {
			llm = "ok".equals(keyStatus) ? "OK" : "ERROR";
			embedding = "ok".equals(keyStatus) ? "OK" : "ERROR";
		} else {
			llm = "MOCK";
			embedding = "MOCK";
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("status", keyStatus);
		result.put("aiMode", aiMode);
		result.put("provider", "openrouter");
		result.put("model", model);
		result.put("embeddingModel", embeddingModel);
		result.put("llm", llm);
		result.put("embedding", embedding);
		return result;
	}

}
