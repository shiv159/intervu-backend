package com.intervu.resumejd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.intervu.resumejd.ResumeJdDtos.CreateJdRequest;
import static com.intervu.resumejd.ResumeJdDtos.JdExtract;
import static com.intervu.resumejd.ResumeJdDtos.ResumeExtract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeJdControllerTests {

	@Mock
	ResumeJdService resumeJdService;

	@Mock
	ResumeJdRepository resumeJdRepository;

	private ResumeJdController controller() {
		return new ResumeJdController(resumeJdService, resumeJdRepository);
	}

	private ResumeExtract sampleResume(UUID id) {
		return new ResumeExtract(id, "owner-1", "cv.pdf", "text", List.of("java"), List.of("scaling"),
			List.of("shipped feature"), "Backend Engineer", "SENIOR", "tika-1", false, Instant.now());
	}

	private JdExtract sampleJd(UUID id) {
		return new JdExtract(id, "owner-1", "jd text", List.of("requires java"), List.of("java"),
			List.of("build services"), "SENIOR", "tika-1", false, Instant.now());
	}

	@Test
	void uploadResumePersistsAndReturnsResponse() throws Exception {
		UUID id = UUID.randomUUID();
		MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf", "Java Spring Boot".getBytes());
		when(resumeJdService.extractText(any(MultipartFile.class))).thenReturn("Java Spring Boot");
		when(resumeJdService.parseResume(any(UUID.class), any(), any(), any()))
			.thenReturn(sampleResume(id));

		var response = controller().uploadResume("owner-1", file);

		verify(resumeJdRepository).insertResume(any(ResumeExtract.class));
		assertThat(response.id()).isEqualTo(id);
		assertThat(response.skills()).contains("java");
		assertThat(response.targetRole()).isEqualTo("Backend Engineer");
	}

	@Test
	void uploadResumeRejectsOversizedFile() {
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		when(file.getSize()).thenReturn(6L * 1024 * 1024);

		assertThatThrownBy(() -> controller().uploadResume("owner-1", file))
			.isInstanceOf(ResponseStatusException.class)
			.hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
	}

	@Test
	void uploadResumeRejectsUnsupportedType() {
		MockMultipartFile file = new MockMultipartFile("file", "cv.txt", "text/plain", "x".getBytes());

		assertThatThrownBy(() -> controller().uploadResume("owner-1", file))
			.isInstanceOf(ResponseStatusException.class)
			.hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
	}

	@Test
	void uploadResumeRejectsDocExtension() {
		MockMultipartFile file = new MockMultipartFile("file", "cv.doc", "application/msword", "x".getBytes());

		assertThatThrownBy(() -> controller().uploadResume("owner-1", file))
			.isInstanceOf(ResponseStatusException.class)
			.hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
	}

	@Test
	void uploadResumeRejectsUnsupportedMediaType() {
		MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "text/plain", "x".getBytes());

		assertThatThrownBy(() -> controller().uploadResume("owner-1", file))
			.isInstanceOf(ResponseStatusException.class)
			.hasFieldOrPropertyWithValue("status", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
	}

	@Test
	void createJdPersistsAndReturnsResponse() {
		UUID id = UUID.randomUUID();
		when(resumeJdService.parseJd(any(UUID.class), any(), any())).thenReturn(sampleJd(id));

		var response = controller().createJd("owner-1", new CreateJdRequest("We require Java"));

		verify(resumeJdRepository).insertJd(any(JdExtract.class));
		assertThat(response.id()).isEqualTo(id);
		assertThat(response.technologies()).contains("java");
	}

	@Test
	void createJdRejectsBlankSourceText() {
		assertThatThrownBy(() -> controller().createJd("owner-1", new CreateJdRequest("   ")))
			.isInstanceOf(ResponseStatusException.class)
			.hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
	}

	@Test
	void latestResumeReturnsNotFoundWhenAbsent() {
		when(resumeJdRepository.findLatestResume("owner-1")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> controller().latestResume("owner-1"))
			.isInstanceOf(ResponseStatusException.class)
			.hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
	}

	@Test
	void latestResumeReturnsStoredExtract() {
		UUID id = UUID.randomUUID();
		when(resumeJdRepository.findLatestResume("owner-1")).thenReturn(Optional.of(sampleResume(id)));

		var response = controller().latestResume("owner-1");

		assertThat(response.id()).isEqualTo(id);
		assertThat(response.skills()).contains("java");
	}

	@Test
	void deleteResumeDelegatesToRepository() {
		UUID id = UUID.randomUUID();
		controller().deleteResume("owner-1", id);
		verify(resumeJdRepository).softDeleteResume(id, "owner-1");
	}

	@Test
	void deleteJdDelegatesToRepository() {
		UUID id = UUID.randomUUID();
		controller().deleteJd("owner-1", id);
		verify(resumeJdRepository).softDeleteJd(id, "owner-1");
	}
}
