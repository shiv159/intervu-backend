package com.intervu.questionbank;

import java.util.List;

public record ImportReport(
    int importedCount,
    int failedCount,
    int skippedCount,
    List<String> errors
) {}
