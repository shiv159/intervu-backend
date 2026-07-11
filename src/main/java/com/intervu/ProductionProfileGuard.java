package com.intervu;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionProfileGuard {

	private static final String DEFAULT_JWT_SECRET = "defaultSuperSecretKeyForIntervuMvpThatIsLongEnoughToUseForHMACSHA256";

	private final Environment environment;
	private final String jwtSecret;

	public ProductionProfileGuard(
		Environment environment,
		@Value("${intervu.jwt.secret:" + DEFAULT_JWT_SECRET + "}") String jwtSecret
	) {
		this.environment = environment;
		this.jwtSecret = jwtSecret;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void validate() {
		if (!isProduction()) {
			return;
		}
		if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.equals(DEFAULT_JWT_SECRET)) {
			throw new IllegalStateException(
				"Production profile requires a real INTERVU_JWT_SECRET; dev defaults are not allowed."
			);
		}
	}

	private boolean isProduction() {
		for (String profile : environment.getActiveProfiles()) {
			if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
				return true;
			}
		}
		return false;
	}
}
