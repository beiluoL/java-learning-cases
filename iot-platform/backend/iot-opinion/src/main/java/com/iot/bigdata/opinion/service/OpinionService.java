package com.iot.bigdata.opinion.service;

import com.iot.bigdata.common.util.DateUtils;
import com.iot.bigdata.opinion.analyzer.SentimentAnalyzer;
import com.iot.bigdata.opinion.entity.OpinionRecord;
import com.iot.bigdata.opinion.repository.OpinionRepository;
import com.iot.bigdata.opinion.vo.HotTopicVO;
import com.iot.bigdata.opinion.vo.OpinionStatVO;
import com.iot.bigdata.opinion.vo.OpinionTrendVO;
import com.iot.bigdata.opinion.event.OpinionCreatedEvent;
import com.iot.bigdata.opinion.vo.OpinionVO;
import com.iot.bigdata.common.constant.IotConstants;
import com.iot.bigdata.common.enums.ResultCode;
import com.iot.bigdata.common.exception.BusinessException;
import com.iot.bigdata.common.result.PageResult;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 舆情服务。提供情感分析、持久化、分页检索、统计与趋势聚合。
 */
@Slf4j
@Service
public class OpinionService {

    private final OpinionRepository opinionRepository;
    private final SentimentAnalyzer sentimentAnalyzer;
    private final ApplicationEventPublisher eventPublisher;

    public OpinionService(OpinionRepository opinionRepository,
                          SentimentAnalyzer sentimentAnalyzer,
                          ApplicationEventPublisher eventPublisher) {
        this.opinionRepository = opinionRepository;
        this.sentimentAnalyzer = sentimentAnalyzer;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 分析并保存一条舆情（自动情感打标）。
     */
    @Transactional
    public OpinionVO analyzeAndSave(String source, String author, String content,
                                    String topic, Integer hot, LocalDateTime publishTime, String url) {
        SentimentAnalyzer.SentimentResult result = sentimentAnalyzer.analyze(content);
        OpinionRecord record = new OpinionRecord();
        record.setSource(source);
        record.setAuthor(author);
        record.setContent(content);
        record.setTopic(topic);
        record.setHot(hot == null ? 0 : hot);
        record.setSentiment(result.getSentiment());
        record.setSentimentScore(BigDecimal.valueOf(result.getScore()));
        record.setPublishTime(publishTime == null ? LocalDateTime.now() : publishTime);
        record.setStatDate((publishTime == null ? LocalDateTime.now() : publishTime).toLocalDate());
        record.setUrl(url);
        OpinionVO saved = toVO(opinionRepository.save(record));
        eventPublisher.publishEvent(new OpinionCreatedEvent(this, saved));
        return saved;
    }

    public PageResult<OpinionVO> page(String sentiment, String topic, String source,
                                      Long pageNum, Long pageSize) {
        long pn = pageNum == null ? IotConstants.DEFAULT_PAGE_NUM : pageNum;
        long ps = pageSize == null ? IotConstants.DEFAULT_PAGE_SIZE : pageSize;
        Pageable pageable = PageRequest.of((int) (pn - 1), (int) ps);

        Specification<OpinionRecord> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(sentiment)) {
                predicates.add(cb.equal(root.get("sentiment"), sentiment));
            }
            if (StringUtils.hasText(topic)) {
                predicates.add(cb.equal(root.get("topic"), topic));
            }
            if (StringUtils.hasText(source)) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            predicates.add(cb.equal(root.get("deleted"), IotConstants.DELETED_NO));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<OpinionRecord> page = opinionRepository.findAll(spec, pageable);
        List<OpinionVO> records = page.getContent().stream().map(this::toVO).toList();
        return PageResult.of(records, page.getTotalElements(), pn, ps);
    }

    public OpinionStatVO statistics() {
        long positive = opinionRepository.countBySentiment("POSITIVE");
        long negative = opinionRepository.countBySentiment("NEGATIVE");
        long neutral = opinionRepository.countBySentiment("NEUTRAL");
        long total = positive + negative + neutral;
        OpinionStatVO vo = new OpinionStatVO();
        vo.setTotal(total);
        vo.setPositive(positive);
        vo.setNegative(negative);
        vo.setNeutral(neutral);
        vo.setNegativeRatio(total == 0 ? 0.0 : (double) negative / total);
        vo.setSentimentIndex(total == 0 ? 0.0 : (double) (positive - negative) / total);
        return vo;
    }

    public List<OpinionTrendVO> trend(int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1);
        List<Object[]> rows = opinionRepository.trendByDate(start, end);
        Map<LocalDate, OpinionTrendVO> map = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            OpinionTrendVO vo = new OpinionTrendVO();
            vo.setDate(d.format(DateUtils.DATE_FORMATTER));
            vo.setPositive(0);
            vo.setNegative(0);
            vo.setNeutral(0);
            map.put(d, vo);
        }
        for (Object[] row : rows) {
            LocalDate date = (LocalDate) row[0];
            String sentiment = (String) row[1];
            long count = ((Number) row[2]).longValue();
            OpinionTrendVO vo = map.get(date);
            if (vo == null) {
                continue;
            }
            if ("POSITIVE".equals(sentiment)) {
                vo.setPositive(count);
            } else if ("NEGATIVE".equals(sentiment)) {
                vo.setNegative(count);
            } else {
                vo.setNeutral(count);
            }
        }
        return new ArrayList<>(map.values());
    }

    public List<HotTopicVO> hotTopics(int days, int topN) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1);
        List<Object[]> rows = opinionRepository.topicHeat(start, end);
        List<HotTopicVO> list = new ArrayList<>();
        for (Object[] row : rows) {
            HotTopicVO vo = new HotTopicVO();
            vo.setTopic((String) row[0]);
            vo.setCount(((Number) row[1]).longValue());
            vo.setHot(((Number) row[2]).longValue());
            list.add(vo);
        }
        list.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        return list.size() <= topN ? list : list.subList(0, topN);
    }

    public List<OpinionVO> latest(int n) {
        return opinionRepository.findAll(PageRequest.of(0, n)).getContent().stream()
                .filter(o -> Objects.equals(o.getDeleted(), IotConstants.DELETED_NO))
                .map(this::toVO)
                .toList();
    }

    public OpinionVO get(Long id) {
        OpinionRecord record = opinionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "舆情不存在: " + id));
        return toVO(record);
    }

    private OpinionVO toVO(OpinionRecord record) {
        OpinionVO vo = new OpinionVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }
}
