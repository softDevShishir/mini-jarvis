package com.example.minijarvis.recommendation;

public class RecommendationNotFoundException extends RuntimeException {
    public RecommendationNotFoundException(long id) {
        super("Recommendation " + id + " not found");
    }
}
