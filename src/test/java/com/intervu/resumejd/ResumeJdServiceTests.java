package com.intervu.resumejd;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.intervu.resumejd.ResumeJdDtos.JdExtract;
import static com.intervu.resumejd.ResumeJdDtos.ResumeExtract;
import static org.assertj.core.api.Assertions.assertThat;

class ResumeJdServiceTests {

	private final ResumeJdService service = new ResumeJdService();

	@Test
	void parseResumeDetectsSkillsAndFocusAreas() {
		String text = """
			Senior Backend Engineer
			I build services with Java, Spring Boot, Kafka and PostgreSQL.
			Strong focus on concurrency, scalability and performance.
			""";
		ResumeExtract extract = service.parseResume(UUID.randomUUID(), "owner-1", "cv.pdf", text);

		assertThat(extract.skills()).contains("java", "kafka", "postgresql", "spring boot");
		assertThat(extract.focusAreas()).contains("concurrency", "scalability", "performance");
		assertThat(extract.seniority()).isEqualTo("SENIOR");
		assertThat(extract.targetRole()).contains("Engineer");
		assertThat(extract.deleted()).isFalse();
		assertThat(extract.parserVersion()).isNotBlank();
		assertThat(extract.createdAt()).isBeforeOrEqualTo(Instant.now());
	}

	@Test
	void parseResumeCapturesClaimLines() {
		String text = """
			- Improved throughput by 40%
			* Reduced p99 latency from 800ms to 120ms
			Some narrative paragraph that should be ignored.
			""";
		ResumeExtract extract = service.parseResume(UUID.randomUUID(), "owner-1", "cv.pdf", text);

		assertThat(extract.claims()).anyMatch(c -> c.contains("40%"));
		assertThat(extract.claims()).anyMatch(c -> c.contains("latency"));
		assertThat(extract.claims()).noneMatch(c -> c.contains("narrative paragraph"));
	}

	@Test
	void parseJdExtractsTechnologiesAndResponsibilities() {
		String text = """
			We require 5+ years with Java and Kubernetes.
			Responsibilities:
			- Design distributed systems
			- Own the deployment pipeline
			You will build event-driven services using Kafka.
			""";
		JdExtract extract = service.parseJd(UUID.randomUUID(), "owner-1", text);

		assertThat(extract.technologies()).contains("java", "kubernetes", "kafka");
		assertThat(extract.requirements()).anyMatch(r -> r.contains("require"));
		assertThat(extract.responsibilities()).anyMatch(r -> r.contains("Design distributed"));
		assertThat(extract.responsibilities()).anyMatch(r -> r.contains("build event-driven"));
	}

	@Test
	void parseHandlesNullTextGracefully() {
		ResumeExtract resume = service.parseResume(UUID.randomUUID(), "owner-1", null, null);
		assertThat(resume.extractedText()).isEmpty();
		assertThat(resume.skills()).isEmpty();

		JdExtract jd = service.parseJd(UUID.randomUUID(), "owner-1", null);
		assertThat(jd.sourceText()).isEmpty();
		assertThat(jd.technologies()).isEmpty();
	}
}
