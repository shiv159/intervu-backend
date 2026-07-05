package com.intervu.questionbank;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class QuestionBankImportService {

    private static final Set<String> VALID_MODES = Set.of("CODE", "SYSTEM_DESIGN", "CONVERSATIONAL");
    private static final Set<String> VALID_DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");
    private static final Set<String> VALID_SENIORITIES = Set.of("JUNIOR", "MID", "SENIOR", "STAFF");

    private final String questionBankPath;
    private final QuestionAdminRepository questionAdminRepository;
    private final ObjectMapper objectMapper;

    public QuestionBankImportService(
            @Value("${intervu.questionbank.path:../../question-bank}") String questionBankPath,
            QuestionAdminRepository questionAdminRepository,
            ObjectMapper objectMapper) {
        this.questionBankPath = questionBankPath;
        this.questionAdminRepository = questionAdminRepository;
        this.objectMapper = objectMapper;
    }

    public ImportReport importQuestions() {
        int importedCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        List<String> errors = new ArrayList<>();

        Path basePath = Paths.get(questionBankPath);
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            errors.add("Question bank directory not found at: " + basePath.toAbsolutePath());
            return new ImportReport(importedCount, failedCount, skippedCount, errors);
        }

        try (Stream<Path> paths = Files.walk(basePath)) {
            List<Path> jsonFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .toList();

            for (Path jsonFile : jsonFiles) {
                try {
                    QuestionDef rawDef = objectMapper.readValue(jsonFile.toFile(), QuestionDef.class);
                    QuestionDef normalized = normalize(rawDef);

                    List<String> validationErrors = validate(normalized);
                    if (!validationErrors.isEmpty()) {
                        failedCount++;
                        errors.add("File " + jsonFile.getFileName() + " invalid: " + String.join(", ", validationErrors));
                        continue;
                    }

                    if (questionAdminRepository.exists(normalized.title(), normalized.mode(), normalized.seniority())) {
                        skippedCount++;
                        continue;
                    }

                    questionAdminRepository.insertDraftQuestion(UUID.randomUUID(), normalized);
                    importedCount++;
                } catch (Exception e) {
                    failedCount++;
                    errors.add("File " + jsonFile.getFileName() + " error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            errors.add("Failed to walk directory: " + e.getMessage());
        }

        return new ImportReport(importedCount, failedCount, skippedCount, errors);
    }

    private List<String> validate(QuestionDef def) {
        List<String> errors = new ArrayList<>();
        if (def.title() == null || def.title().isBlank()) errors.add("Missing title");
        if (def.prompt() == null || def.prompt().isBlank()) errors.add("Missing prompt");
        
        if (def.mode() == null || def.mode().isBlank()) {
            errors.add("Missing mode");
        } else if (!VALID_MODES.contains(def.mode())) {
            errors.add("Invalid mode: " + def.mode());
        }

        if (def.difficulty() == null || def.difficulty().isBlank()) {
            errors.add("Missing difficulty");
        } else if (!VALID_DIFFICULTIES.contains(def.difficulty())) {
            errors.add("Invalid difficulty: " + def.difficulty());
        }

        if (def.seniority() == null || def.seniority().isBlank()) {
            errors.add("Missing seniority");
        } else if (!VALID_SENIORITIES.contains(def.seniority())) {
            errors.add("Invalid seniority: " + def.seniority());
        }
        
        if (def.tags() == null || def.tags().isEmpty()) {
            errors.add("Tags cannot be empty");
        }
        if (def.expectedConcepts() == null || def.expectedConcepts().isEmpty()) {
            errors.add("Expected concepts cannot be empty");
        }
        if (def.rubric() == null || def.rubric().isEmpty()) {
            errors.add("Rubric cannot be empty");
        }
        return errors;
    }

    private QuestionDef normalize(QuestionDef def) {
        String mode = normalizeEnum(def.mode());
        String difficulty = normalizeEnum(def.difficulty());
        String seniority = normalizeEnum(def.seniority());
        
        List<String> tags = def.tags() == null ? List.of() : def.tags().stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim().toLowerCase(Locale.ROOT).replace(' ', '-'))
                .toList();

        return new QuestionDef(
                def.title(),
                def.prompt(),
                mode,
                difficulty,
                seniority,
                tags,
                def.expectedConcepts() == null ? List.of() : def.expectedConcepts(),
                def.rubric() == null ? java.util.Collections.emptyMap() : def.rubric()
        );
    }

    private String normalizeEnum(String val) {
        if (val == null) return "";
        return val.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }
}
