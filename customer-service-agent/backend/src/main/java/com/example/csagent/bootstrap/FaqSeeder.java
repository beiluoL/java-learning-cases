package com.example.csagent.bootstrap;

import com.example.csagent.model.FaqItem;
import com.example.csagent.repository.FaqItemRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时若 FAQ 知识库为空则灌入示例数据（可直接在数据库/管理后台增删改）。
 * 覆盖了退款、查询订单、技术支持三大意图及通用问题。
 */
@Component
public class FaqSeeder implements CommandLineRunner {

    private final FaqItemRepository faqRepository;

    public FaqSeeder(FaqItemRepository faqRepository) {
        this.faqRepository = faqRepository;
    }

    @Override
    public void run(String... args) {
        if (faqRepository.count() > 0) return;

        List<FaqItem> seed = List.of(
                // ===== 退款 =====
                faq("REFUND", "如何申请退款？",
                        "您可在「我的订单」中找到对应订单，点击「申请退款」，选择退款原因后提交。审核通过后 1-3 个工作日原路退回。",
                        "退款,退货,退钱,申请退款,不想要了"),
                faq("REFUND", "退款多久能到账？",
                        "退款审核通过后，款项将原路退回：微信/支付宝一般 1-3 个工作日到账，银行卡 3-7 个工作日，请留意账户变动。",
                        "退款到账,多久,几天,到账时间,退款进度"),
                faq("REFUND", "退款被拒绝了怎么办？",
                        "若退款被拒，常见原因是商品已拆封使用或超出售后期限。您可联系人工客服上传凭证申诉，我们会尽快复核。",
                        "退款拒绝,拒收,申诉,不同意退款,驳回"),
                faq("REFUND", "已发货的订单能退款吗？",
                        "已发货订单可拒收后自动退款，或在收到货后申请「七天无理由退货」。如已签收，请在订单页发起退货退款流程。",
                        "已发货,拒收,退货退款,七天无理由,签收"),

                // ===== 查询订单 =====
                faq("ORDER_QUERY", "怎么查看我的订单物流？",
                        "进入「我的订单」选择对应订单，点击「查看物流」即可实时追踪快递位置与预计送达时间。",
                        "物流,快递,发货,到哪了,查订单,单号,配送"),
                faq("ORDER_QUERY", "订单号在哪里看？",
                        "打开「我的订单」，每个订单卡片顶部均显示 18 位订单编号（以 No. 开头），点击可复制。",
                        "订单号,单号,编号,在哪里,查订单"),
                faq("ORDER_QUERY", "订单迟迟未发货怎么办？",
                        "普通商品付款后 48 小时内发货。若超时未发货，可催促卖家或申请退款；预售商品以页面标注发货时间为准。",
                        "未发货,迟迟,什么时候发货,催发货,预售"),
                faq("ORDER_QUERY", "如何修改收货地址？",
                        "订单未发货前，可在订单详情页点击「修改地址」。已发货则无法修改，请拒收或联系快递改派。",
                        "改地址,收货地址,地址错了,修改地址"),
                faq("ORDER_QUERY", "支持开发票吗？",
                        "支持。在订单完成后进入「申请发票」，填写抬头与税号，电子发票将于 1-2 个工作日发送至您的邮箱。",
                        "发票,开票,抬头,税号,电子发票"),

                // ===== 技术支持 =====
                faq("TECH_SUPPORT", "无法登录账号怎么办？",
                        "请先确认网络正常，尝试「短信验证码登录」或「重置密码」。多次失败可能触发安全锁定，30 分钟后自动解除，或联系人工解封。",
                        "登录,登不上,无法登录,密码,验证码,锁定,封号"),
                faq("TECH_SUPPORT", "App 闪退/打不开怎么办？",
                        "建议：1）清理后台后重启；2）升级到最新版本；3）卸载重装（数据已云端同步）。如仍异常，请提供机型与系统版本联系我们。",
                        "闪退,打不开,崩溃,卡死,黑屏,bug,重装"),
                faq("TECH_SUPPORT", "支付失败如何处理？",
                        "多为银行卡限额或网络超时。请更换支付方式或稍后重试；如已扣款但未生成订单，款项会在 1-3 个工作日自动退回。",
                        "支付失败,扣款,付不了,付款,限额,支付异常"),
                faq("TECH_SUPPORT", "如何绑定/更换手机号？",
                        "在「设置-账号安全-手机号」中验证原手机号后更换。若原号已停用，需人工客服核验身份后处理。",
                        "换手机号,绑定手机,修改手机,手机号"),
                faq("TECH_SUPPORT", "找不到某个功能在哪里？",
                        "您可以在首页搜索框直接搜索功能名称，或进入「我的-全部服务」按分类查找。也可描述具体需求由我帮您定位。",
                        "找不到,功能,在哪,怎么用,教程,设置,使用"),

                // ===== 通用 =====
                faq("GENERAL", "你们的客服工作时间？",
                        "在线智能客服 7×24 小时为您服务；人工客服工作时间为每日 9:00-21:00，非工作时段留言将于次日优先处理。",
                        "客服时间,上班时间,人工几点,工作时间"),
                faq("GENERAL", "怎么联系人工客服？",
                        "您可以随时在对话框输入「转人工」或直接点击「转人工」按钮，系统会为您创建工单并接入人工坐席。",
                        "人工,转人工,客服,真人,坐席")
        );

        faqRepository.saveAll(seed);
        System.out.println("[FaqSeeder] 已灌入 " + seed.size() + " 条 FAQ 知识库示例数据。");
    }

    private FaqItem faq(String category, String question, String answer, String keywords) {
        FaqItem item = new FaqItem();
        item.setCategory(category);
        item.setQuestion(question);
        item.setAnswer(answer);
        item.setKeywords(keywords);
        item.setWeight(1);
        return item;
    }
}
