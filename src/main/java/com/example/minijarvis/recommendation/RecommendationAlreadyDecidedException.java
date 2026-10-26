package com.example.minijarvis.recommendation;

public class RecommendationAlreadyDecidedException extends RuntimeException {
    public RecommendationAlreadyDecidedException(long id, RecommendationStatus status) {
        super("Recommendation " + id + " is already " + status);
    }
}
