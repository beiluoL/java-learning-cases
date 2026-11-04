package com.example.redislab.service;

import com.example.redislab.modules.rank.RankItem;

import java.util.List;

public interface RankService {

    /** 给成员加分（同时更新「今日榜」与「总榜」） */
    void addScore(String member, double incr);

    /** 今日榜 TopN */
    List<RankItem> topN(int n);

    /** 总榜 TopN */
    List<RankItem> topNTotal(int n);

    /** 某成员今日榜名次（从 1 开始，未上榜返回 null） */
    Long rankOf(String member);

    /** 某成员今日榜分数 */
    Double getScore(String member);
}
