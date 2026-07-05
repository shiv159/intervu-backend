package com.intervu.questionbank.embedding;

import java.util.List;

public record EmbeddingResult(String model, List<Double> embedding) {
}
