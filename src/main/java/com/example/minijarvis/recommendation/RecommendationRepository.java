package com.example.minijarvis.recommendation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findByStatusOrderByIdDesc(RecommendationStatus status);

    List<Recommendation> findAllByOrderByIdDesc();

    List<Recommendation> findByAgentRunIdOrderByIdAsc(Long agentRunId);
}
