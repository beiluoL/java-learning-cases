package com.iot.bigdata.analysis.store;

import com.iot.bigdata.analysis.model.AnalysisAlert;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 告警存储（内存有界队列）。保留最近若干条告警供看板与接口读取。
 */
@Service
public class AlertStore {

    /** 最大保留条数。 */
    private static final int MAX_SIZE = 200;

    private final ConcurrentLinkedDeque<AnalysisAlert> deque = new ConcurrentLinkedDeque<>();

    /**
     * 新增告警（最新在前）。
     */
    public void add(AnalysisAlert alert) {
        deque.addFirst(alert);
        while (deque.size() > MAX_SIZE) {
            deque.pollLast();
        }
    }

    /**
     * 返回最近 n 条告警（最新优先）。
     */
    public List<AnalysisAlert> recent(int n) {
        List<AnalysisAlert> list = new ArrayList<>(deque);
        return list.size() <= n ? list : list.subList(0, n);
    }

    public int size() {
        return deque.size();
    }
}
