package com.example.csagent.repository;

import com.example.csagent.model.Satisfaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SatisfactionRepository extends JpaRepository<Satisfaction, String> {

    List<Satisfaction> findByConversationIdOrderByCreatedAtDesc(String conversationId);

    @Query("SELECT COALESCE(AVG(s.score), 0.0) FROM Satisfaction s")
    Double averageScore();

    @Query("SELECT s.score, COUNT(s) FROM Satisfaction s GROUP BY s.score")
    List<Object[]> scoreDistribution();

    @Query("SELECT s.intent, AVG(s.score), COUNT(s) FROM Satisfaction s GROUP BY s.intent")
    List<Object[]> statsByIntent();
}
