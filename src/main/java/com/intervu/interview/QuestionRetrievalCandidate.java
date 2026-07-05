package com.intervu.interview;

public record QuestionRetrievalCandidate(
	InterviewDtos.QuestionPayload question,
	double distance,
	String embeddedTextHash
) {
}
