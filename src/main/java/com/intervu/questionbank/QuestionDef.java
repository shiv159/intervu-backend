package com.intervu.questionbank;

import java.util.List;
import java.util.Map;

public record QuestionDef(
    String title,
    String prompt,
    String mode,
    String difficulty,
    String seniority,
    List<String> tags,
    List<String> expectedConcepts,
    Map<String, Integer> rubric
) {}
