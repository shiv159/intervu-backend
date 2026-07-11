package com.intervu.resumejd;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.intervu.resumejd.ResumeJdDtos.CreateJdRequest;
import static com.intervu.resumejd.ResumeJdDtos.JdExtractResponse;
import static com.intervu.resumejd.ResumeJdDtos.ResumeExtractResponse;

@RestController
@RequestMapping("/api")
public class ResumeJdController {

	private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
	private static final List<String> ALLOWED_EXTENSIONS = List.of(".pdf", ".docx");
	private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
		"application/pdf",
		"application/vnd.openxmlformats-officedocument.wordprocessingml.document"
	);
	private static final int MAX_RETURNED_TEXT_CHARS = 200;

	private final ResumeJdService resumeJdService;
	private final ResumeJdRepository resumeJdRepository;

	public ResumeJdController(ResumeJdService resumeJdService, ResumeJdRepository resumeJdRepository) {
		this.resumeJdService = resumeJdService;
		this.resumeJdRepository = resumeJdRepository;
	}

	@PostMapping("/resumes")
	public ResumeExtractResponse uploadResume(
		@RequestAttribute("userId") String userId,
		@RequestParam("file") MultipartFile file
	) {
		validateFile(file);
		try {
			String text = resumeJdService.extractText(file);
			ResumeJdDtos.ResumeExtract extract = resumeJdService.parseResume(
				UUID.randomUUID(), userId, file.getOriginalFilename(), text);
			resumeJdRepository.insertResume(extract);
			return toResumeResponse(extract);
		} catch (IOException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not parse resume file: " + ex.getMessage());
		}
	}

	@PostMapping("/jds")
	public JdExtractResponse createJd(
		@RequestAttribute("userId") String userId,
		@RequestBody CreateJdRequest request
	) {
		if (request.sourceText() == null || request.sourceText().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceText is required");
		}
		ResumeJdDtos.JdExtract extract = resumeJdService.parseJd(UUID.randomUUID(), userId, request.sourceText());
		resumeJdRepository.insertJd(extract);
		return toJdResponse(extract);
	}

	@GetMapping("/resumes/latest")
	public ResumeExtractResponse latestResume(@RequestAttribute("userId") String userId) {
		ResumeJdDtos.ResumeExtract extract = resumeJdRepository.findLatestResume(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No resume found for this user"));
		return toResumeResponse(extract);
	}

	@GetMapping("/jds/latest")
	public JdExtractResponse latestJd(@RequestAttribute("userId") String userId) {
		ResumeJdDtos.JdExtract extract = resumeJdRepository.findLatestJd(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No job description found for this user"));
		return toJdResponse(extract);
	}

	@DeleteMapping("/resumes/{id}")
	public ResponseEntity<Void> deleteResume(@RequestAttribute("userId") String userId, @PathVariable UUID id) {
		resumeJdRepository.softDeleteResume(id, userId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/jds/{id}")
	public ResponseEntity<Void> deleteJd(@RequestAttribute("userId") String userId, @PathVariable UUID id) {
		resumeJdRepository.softDeleteJd(id, userId);
		return ResponseEntity.noContent().build();
	}

	private void validateFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume file is required");
		}
		if (file.getSize() > MAX_FILE_BYTES) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume file must be 5MB or smaller");
		}
		String name = file.getOriginalFilename();
		if (name == null || ALLOWED_EXTENSIONS.stream().noneMatch(e -> name.toLowerCase(Locale.ROOT).endsWith(e))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF or DOCX resumes are supported");
		}
		String contentType = file.getContentType();
		if (contentType == null || ALLOWED_CONTENT_TYPES.stream().noneMatch(contentType::equalsIgnoreCase)) {
			throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Resume file must be a PDF or DOCX document");
		}
	}

	private static String redactText(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}
		String trimmed = text.trim();
		if (trimmed.length() <= MAX_RETURNED_TEXT_CHARS) {
			return trimmed;
		}
		return trimmed.substring(0, MAX_RETURNED_TEXT_CHARS) + "... (redacted)";
	}

	private ResumeExtractResponse toResumeResponse(ResumeJdDtos.ResumeExtract e) {
		return new ResumeExtractResponse(
			e.id(), e.sourceFilename(), redactText(e.extractedText()), e.skills(), e.focusAreas(), e.claims(), e.targetRole(), e.seniority());
	}

	private JdExtractResponse toJdResponse(ResumeJdDtos.JdExtract e) {
		return new JdExtractResponse(
			e.id(), redactText(e.sourceText()), e.requirements(), e.technologies(), e.responsibilities(), e.seniority());
	}
}
