package com.example.redislab.modules.rank;

import com.example.redislab.common.R;
import com.example.redislab.service.RankService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景3：排行榜。
 * POST /api/rank/add        加分（member + score 增量）
 * GET  /api/rank/top        今日榜 TopN
 * GET  /api/rank/top/total  总榜 TopN
 * GET  /api/rank/rank/{m}   查某成员名次与分数
 */
@RestController
@RequestMapping("/api/rank")
public class RankController {

    private final RankService rankService;

    public RankController(RankService rankService) {
        this.rankService = rankService;
    }

    @PostMapping("/add")
    public R<Void> add(@RequestParam String member, @RequestParam double score) {
        rankService.addScore(member, score);
        return R.ok();
    }

    @GetMapping("/top")
    public R<List<RankItem>> top(@RequestParam(defaultValue = "10") int n) {
        return R.ok(rankService.topN(n));
    }

    @GetMapping("/top/total")
    public R<List<RankItem>> topTotal(@RequestParam(defaultValue = "10") int n) {
        return R.ok(rankService.topNTotal(n));
    }

    @GetMapping("/rank/{member}")
    public R<Map<String, Object>> rank(@PathVariable String member) {
        Map<String, Object> map = new HashMap<>();
        map.put("member", member);
        map.put("rank", rankService.rankOf(member));
        map.put("score", rankService.getScore(member));
        return R.ok(map);
    }
}
