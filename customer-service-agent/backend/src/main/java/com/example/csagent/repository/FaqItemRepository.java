package com.example.csagent.repository;

import com.example.csagent.model.FaqItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FaqItemRepository extends JpaRepository<FaqItem, Long> {
    List<FaqItem> findByCategoryOrderByWeightDesc(String category);
    List<FaqItem> findAllByOrderByCategoryAscWeightDesc();
}
