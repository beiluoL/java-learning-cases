package com.iot.bigdata.opinion.repository;

import com.iot.bigdata.opinion.entity.OpinionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 舆情数据访问层。
 */
@Repository
public interface OpinionRepository extends JpaRepository<OpinionRecord, Long>, JpaSpecificationExecutor<OpinionRecord> {

    Page<OpinionRecord> findBySentiment(String sentiment, Pageable pageable);

    long countBySentiment(String sentiment);

    List<OpinionRecord> findByPublishTimeBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 按天 + 情感倾向统计数量，用于舆情趋势图。
     *
     * @return 每行 [statDate, sentiment, count]
     */
    @Query("SELECT o.statDate, o.sentiment, COUNT(o) FROM OpinionRecord o "
            + "WHERE o.statDate BETWEEN :start AND :end GROUP BY o.statDate, o.sentiment")
    List<Object[]> trendByDate(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * 话题热度排行（按数量降序）。
     *
     * @return 每行 [topic, count, sumHot]
     */
    @Query("SELECT o.topic, COUNT(o), COALESCE(SUM(o.hot),0) FROM OpinionRecord o "
            + "WHERE o.statDate BETWEEN :start AND :end GROUP BY o.topic")
    List<Object[]> topicHeat(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
