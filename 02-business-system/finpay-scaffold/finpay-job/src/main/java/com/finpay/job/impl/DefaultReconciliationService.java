package com.finpay.job.impl;

import com.finpay.job.model.*;
import com.finpay.job.service.ReconciliationService;
import com.finpay.job.spi.BillSource;
import com.finpay.job.spi.LocalOrderSource;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 默认对账实现。核心比对算法：
 * 1. 以 outTradeNo 为键，分别建立本地/渠道索引。
 * 2. 本地有、渠道无 → LOCAL_ONLY（疑似掉单，需补查或补偿）。
 * 3. 渠道有、本地无 → CHANNEL_ONLY（疑似漏单/异常）。
 * 4. 双方都有 → 比金额与状态，产出 AMOUNT_DIFF / STATUS_DIFF。
 */
@Service
public class DefaultReconciliationService implements ReconciliationService {

    private final LocalOrderSource localOrderSource;
    private final BillSource billSource;

    public DefaultReconciliationService(LocalOrderSource localOrderSource, BillSource billSource) {
        this.localOrderSource = localOrderSource;
        this.billSource = billSource;
    }

    @Override
    public ReconcileReport reconcile(String channel, String date) {
        List<ReconciliationBill> local = localOrderSource.fetchLocal(channel, date);
        List<ReconciliationBill> remote = billSource.fetchBill(channel, date);

        Map<String, ReconciliationBill> localMap = index(local);
        Map<String, ReconciliationBill> remoteMap = index(remote);

        List<Mismatch> mismatches = new ArrayList<>();
        int matched = 0;

        for (Map.Entry<String, ReconciliationBill> e : localMap.entrySet()) {
            ReconciliationBill r = remoteMap.get(e.getKey());
            if (r == null) {
                Mismatch m = new Mismatch();
                m.setType(Mismatch.Type.LOCAL_ONLY);
                m.setOutTradeNo(e.getKey());
                m.setLocal(e.getValue());
                m.setDescription("我方有订单但渠道无记录（可能掉单）");
                mismatches.add(m);
            } else {
                Mismatch m = compare(e.getValue(), r);
                if (m != null) mismatches.add(m);
                else matched++;
            }
        }
        for (Map.Entry<String, ReconciliationBill> e : remoteMap.entrySet()) {
            if (!localMap.containsKey(e.getKey())) {
                Mismatch m = new Mismatch();
                m.setType(Mismatch.Type.CHANNEL_ONLY);
                m.setOutTradeNo(e.getKey());
                m.setChannel(e.getValue());
                m.setDescription("渠道有记录但我方无订单（可能漏单/异常）");
                mismatches.add(m);
            }
        }

        ReconcileReport report = new ReconcileReport();
        report.setChannel(channel);
        report.setDate(date);
        report.setLocalCount(local.size());
        report.setChannelCount(remote.size());
        report.setMatchedCount(matched);
        report.setMismatches(mismatches);
        return report;
    }

    private Mismatch compare(ReconciliationBill l, ReconciliationBill r) {
        if (l.getAmount() != null && r.getAmount() != null && l.getAmount().compareTo(r.getAmount()) != 0) {
            Mismatch m = new Mismatch();
            m.setType(Mismatch.Type.AMOUNT_DIFF);
            m.setOutTradeNo(l.getOutTradeNo());
            m.setLocal(l); m.setChannel(r);
            m.setDescription("金额不符 本地=" + l.getAmount() + " 渠道=" + r.getAmount());
            return m;
        }
        if (!Objects.equals(l.getStatus(), r.getStatus())) {
            Mismatch m = new Mismatch();
            m.setType(Mismatch.Type.STATUS_DIFF);
            m.setOutTradeNo(l.getOutTradeNo());
            m.setLocal(l); m.setChannel(r);
            m.setDescription("状态不符 本地=" + l.getStatus() + " 渠道=" + r.getStatus());
            return m;
        }
        return null;
    }

    private Map<String, ReconciliationBill> index(List<ReconciliationBill> list) {
        Map<String, ReconciliationBill> map = new HashMap<>();
        for (ReconciliationBill b : list) map.put(b.getOutTradeNo(), b);
        return map;
    }
}
