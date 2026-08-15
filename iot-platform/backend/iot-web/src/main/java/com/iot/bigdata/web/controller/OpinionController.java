package com.iot.bigdata.web.controller;

import com.iot.bigdata.common.result.ApiResult;
import com.iot.bigdata.opinion.service.OpinionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 舆情接口。提供舆情列表、情感统计、趋势与热点话题查询。
 */
@RestController
@RequestMapping("/api/opinion")
public class OpinionController {

    private final OpinionService opinionService;

    public OpinionController(OpinionService opinionService) {
        this.opinionService = opinionService;
    }

    @GetMapping("/list")
    public ApiResult<?> list(@RequestParam(required = false) String sentiment,
                             @RequestParam(required = false) String topic,
                             @RequestParam(required = false) String source,
                             @RequestParam(defaultValue = "1") Long pageNum,
                             @RequestParam(defaultValue = "20") Long pageSize) {
        return ApiResult.success(opinionService.page(sentiment, topic, source, pageNum, pageSize));
    }

    @GetMapping("/stat")
    public ApiResult<?> stat() {
        return ApiResult.success(opinionService.statistics());
    }

    @GetMapping("/trend")
    public ApiResult<?> trend(@RequestParam(defaultValue = "7") int days) {
        return ApiResult.success(opinionService.trend(days));
    }

    @GetMapping("/hot")
    public ApiResult<?> hot(@RequestParam(defaultValue = "7") int days,
                            @RequestParam(defaultValue = "10") int topN) {
        return ApiResult.success(opinionService.hotTopics(days, topN));
    }
}
