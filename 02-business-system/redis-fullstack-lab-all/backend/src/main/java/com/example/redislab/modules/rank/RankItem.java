package com.example.redislab.modules.rank;

/**
 * 排行榜条目：成员、分数、名次。
 */
public record RankItem(String member, double score, long rank) {
}
