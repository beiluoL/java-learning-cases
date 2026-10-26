package com.iot.bigdata.opinion.simulator;

import com.iot.bigdata.opinion.service.OpinionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

/**
 * 舆情模拟器。生成贴近真实的样本舆情（含正/负向表达），用于演示采集与情感分析链路。
 *
 * <p>生产环境应替换为真实的舆情采集适配（爬虫 / 开放平台 API / 消息队列订阅）。</p>
 */
@Component
public class OpinionSimulator {

    private static final Logger log = LoggerFactory.getLogger(OpinionSimulator.class);

    private final OpinionService opinionService;
    private final Random random = new Random();

    private static final List<String> TOPICS =
            List.of("产品质量", "售后服务", "物流速度", "价格优惠", "使用体验", "App稳定性");
    private static final List<String> SOURCES =
            List.of("微博", "抖音", "新闻", "论坛", "小红书");

    private static final List<String> POSITIVE_TEMPLATES = List.of(
            "这款产品的{topic}真的稳定，给力！",
            "{topic}让我很满意，强烈推荐给大家。",
            "体验了下{topic}，流畅又方便，正品无疑。",
            "{topic}服务太贴心了，效率超高，赞！"
    );
    private static final List<String> NEGATIVE_TEMPLATES = List.of(
            "{topic}太差了，经常出现故障，差评！",
            "对{topic}很失望，又卡又慢，虚假宣传的吧。",
            "{topic}问题频出，客服还推诿，投诉无门。",
            "这个{topic}体验垃圾，发热严重，后悔购买。"
    );
    private static final List<String> NEUTRAL_TEMPLATES = List.of(
            "今天关注了一下{topic}，感觉还行吧。",
            "关于{topic}，网上说法不一，再观望。",
            "{topic}这个话题最近讨论挺多的。"
    );

    public OpinionSimulator(OpinionService opinionService) {
        this.opinionService = opinionService;
    }

    /**
     * 生成并入库指定数量的模拟舆情。
     *
     * @param count 数量
     */
    public int generate(int count) {
        for (int i = 0; i < count; i++) {
            String topic = pick(TOPICS);
            int type = random.nextInt(3);
            String template = type == 0 ? pick(POSITIVE_TEMPLATES)
                    : type == 1 ? pick(NEGATIVE_TEMPLATES) : pick(NEUTRAL_TEMPLATES);
            String content = template.replace("{topic}", topic);

            String source = pick(SOURCES);
            String author = "user" + (1000 + random.nextInt(9000));
            int hot = 10 + random.nextInt(5000);
            LocalDateTime publishTime = LocalDateTime.now()
                    .minusMinutes(random.nextInt((int) ChronoUnit.MINUTES.between(
                            LocalDateTime.now().minusDays(7), LocalDateTime.now())));
            opinionService.analyzeAndSave(source, author, content, topic, hot, publishTime, null);
        }
        log.info("[Opinion] 模拟生成舆情 {} 条", count);
        return count;
    }

    private String pick(List<String> list) {
        return list.get(random.nextInt(list.size()));
    }
}
