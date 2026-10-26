package com.example.minijarvis.notification;

import java.util.List;

import com.example.minijarvis.agent.AgentRun;
import com.example.minijarvis.recommendation.Recommendation;

/** Tells the director about new recommendations. Implementations must never throw. */
public interface RecommendationNotifier {

    void notifyNewRecommendations(AgentRun run, List<Recommendation> recommendations);
}
