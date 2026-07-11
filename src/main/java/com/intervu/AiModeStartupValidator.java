package com.intervu;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AiModeStartupValidator {

	private final String aiMode;
	private final String openRouterApiKey;

	@Autowired
	public AiModeStartupValidator(
		@Value("${intervu.ai.mode:MOCK}") String aiMode,
		@Value("${openrouter.api.key:mock_key_for_build}") String openRouterApiKey
	) {
		this.aiMode = aiMode;
		this.openRouterApiKey = openRouterApiKey;
	}

	public AiModeStartupValidator() {
		this(
			System.getProperty("intervu.ai.mode", "MOCK"),
			System.getProperty("openrouter.api.key", "mock_key_for_build")
		);
		validateMode(aiMode, openRouterApiKey);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void validate() {
		validateMode(aiMode, openRouterApiKey);
	}

	public static void validateMode(String aiMode, String openRouterApiKey) {
		if (!"LIVE".equalsIgnoreCase(aiMode)) {
			return;
		}
		if (openRouterApiKey == null || openRouterApiKey.isBlank() || openRouterApiKey.startsWith("mock_key")) {
			throw new IllegalStateException(
				"LIVE mode requires non-mock OPENROUTER_API_KEY. Set INTERVU_AI_MODE=MOCK or provide a real OPENROUTER_API_KEY."
			);
		}
	}
}
