package com.intervu;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AiModeStartupValidatorTests {

	@AfterEach
	void clearLeakedProperties() {
		System.clearProperty("intervu.ai.mode");
		System.clearProperty("openrouter.api.key");
	}

	@Test
	void mockModeWithMockKeyDoesNotThrow() {
		assertThatNoException().isThrownBy(() -> {
			System.setProperty("intervu.ai.mode", "MOCK");
			System.setProperty("openrouter.api.key", "mock_key_for_build");
			new AiModeStartupValidator();
		});
	}

	@Test
	void liveModeWithBlankKeyThrows() {
		assertThatThrownBy(() -> {
			System.setProperty("intervu.ai.mode", "LIVE");
			System.setProperty("openrouter.api.key", "");
			new AiModeStartupValidator();
		}).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void liveModeWithMockKeyThrows() {
		assertThatThrownBy(() -> {
			System.setProperty("intervu.ai.mode", "LIVE");
			System.setProperty("openrouter.api.key", "mock_key_for_build");
			new AiModeStartupValidator();
		}).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void liveModeWithRealKeyDoesNotThrow() {
		assertThatNoException().isThrownBy(() -> {
			System.setProperty("intervu.ai.mode", "LIVE");
			System.setProperty("openrouter.api.key", "sk-real-key-123");
			new AiModeStartupValidator();
		});
	}
}
