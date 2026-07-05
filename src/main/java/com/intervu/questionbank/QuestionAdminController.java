package com.intervu.questionbank;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class QuestionAdminController {

    private final QuestionBankImportService importService;
    private final QuestionPublicationService publicationService;
    private final String expectedAdminToken;

    public QuestionAdminController(
            QuestionBankImportService importService,
            QuestionPublicationService publicationService,
            @Value("${intervu.admin.token:secret-admin-token}") String expectedAdminToken) {
        this.importService = importService;
        this.publicationService = publicationService;
        this.expectedAdminToken = expectedAdminToken;
    }

    private boolean isAuthorized(String token) {
        return expectedAdminToken.equals(token);
    }

    @PostMapping("/question-bank/import")
    public ResponseEntity<?> importQuestions(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!isAuthorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        ImportReport report = importService.importQuestions();
        return ResponseEntity.ok(report);
    }

    @PostMapping("/questions/{id}/publish")
    public ResponseEntity<?> publishQuestion(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!isAuthorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        publicationService.publishQuestion(id);
        return ResponseEntity.ok("Question published successfully");
    }

    @PostMapping("/questions/{id}/embedding/rebuild")
    public ResponseEntity<?> rebuildQuestionEmbedding(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!isAuthorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        publicationService.rebuildEmbedding(id);
        return ResponseEntity.ok("Question embedding rebuilt successfully");
    }

    @PostMapping("/questions/{id}/archive")
    public ResponseEntity<?> archiveQuestion(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!isAuthorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        publicationService.archiveQuestion(id);
        return ResponseEntity.ok("Question archived successfully");
    }
}
