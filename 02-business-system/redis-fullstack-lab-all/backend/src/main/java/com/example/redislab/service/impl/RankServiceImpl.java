package com.example.redislab.service.impl;

import com.example.redislab.common.RedisKeys;
import com.example.redislab.modules.rank.RankItem;
import com.example.redislab.service.RankService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 排行榜（ZSET 实现）。
 *
 * <p>为什么用 ZSET？ZADD/ZINCRBY 自动按 score 排序，ZREVRANGE 取 TopN、ZREVRANK 查名次都是 O(logN)，
 * 天然适合游戏积分榜、商品销量榜、热搜榜。这里用「今日榜 + 总榜」两个 key，
 * 每日批处理把今日榜合并进总榜（演示中直接双写，生产可定时任务汇总）。</p>
 */
@Service
public class RankServiceImpl implements RankService {

    private final StringRedisTemplate stringRedisTemplate;

    public RankServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // 如 20261104
    }

    @Override
    public void addScore(String member, double incr) {
        stringRedisTemplate.opsForZSet().incrementScore(RedisKeys.rankDay(today()), member, incr);
        stringRedisTemplate.opsForZSet().incrementScore(RedisKeys.rankTotal(), member, incr);
    }

    @Override
    public List<RankItem> topN(int n) {
        return readTopN(RedisKeys.rankDay(today()), n);
    }

    @Override
    public List<RankItem> topNTotal(int n) {
        return readTopN(RedisKeys.rankTotal(), n);
    }

    @Override
    public Long rankOf(String member) {
        Long r = stringRedisTemplate.opsForZSet().reverseRank(RedisKeys.rankDay(today()), member);
        return r == null ? null : r + 1;
    }

    @Override
    public Double getScore(String member) {
        return stringRedisTemplate.opsForZSet().score(RedisKeys.rankDay(today()), member);
    }

    private List<RankItem> readTopN(String key, int n) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, 0, n - 1);
        List<RankItem> list = new ArrayList<>();
        if (tuples == null) {
            return list;
        }
        long rank = 1;
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            double score = t.getScore() == null ? 0 : t.getScore();
            list.add(new RankItem(t.getValue(), score, rank++));
        }
        return list;
    }
}
