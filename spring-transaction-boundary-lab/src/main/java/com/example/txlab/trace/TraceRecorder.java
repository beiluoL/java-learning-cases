package com.example.txlab.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量执行轨迹记录器（TraceRecorder）。
 *
 * <p>为什么需要它：只看最终抛出的异常，无法证明"谁先谁后"。本类按真实代码执行顺序，
 * 在每一处关键节点（控制器入口 / 事务进入 / SQL 执行 / 异常捕获 / 方法返回 /
 * 事务同步回调）追加一条带自增序号的事件，从而把真实先后顺序固定下来。</p>
 *
 * <p>这不是伪造日志：事件只在代码真正走到那一行时才被记录，
 * 事务状态字符串也是当场从 {@code TransactionSynchronizationManager} 等公共 API 读出来的。</p>
 */
@Component
public class TraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(TraceRecorder.class);

    /** 单次实验最多保留的事件数，防止长跑累积 */
    private static final int MAX_EVENTS = 1000;

    /** 全局自增序号：跨多次实验也保持单调递增，便于断言"先后" */
    private final AtomicLong sequencer = new AtomicLong();

    /** 事件列表，按写入顺序保存 */
    private final List<Event> events = Collections.synchronizedList(new ArrayList<>());

    /** 本次实验的名字 */
    private volatile String runName = "(未命名实验)";

    /**
     * 一条执行事件。
     *
     * @param seq    全局自增序号
     * @param nanos  写入时刻的 {@link System#nanoTime()}，只用于计算相对间隔
     * @param phase  阶段标签，如 CONTROLLER / TX_OUTER / TX_INNER / SQL / RETURN / TX_SYNC
     * @param message 事件描述
     * @param txState 当时线程绑定的事务状态快照文本，没有事务上下文时为 null
     */
    public record Event(long seq, long nanos, String phase, String message, String txState) {

        /** 渲染成一行文本 */
        public String render() {
            return "[%03d] %-11s %s%s".formatted(
                    seq, phase, message,
                    txState == null ? "" : "  ⇢ " + txState);
        }
    }

    /** 开始一次新实验：清空旧事件，并记录起始事件 */
    public synchronized void startRun(String name) {
        events.clear();
        this.runName = name;
        record("RUN", "===== 开始实验：" + name + " =====");
    }

    /** 记录一条不带事务状态的事件 */
    public Event record(String phase, String message) {
        return record(phase, message, null);
    }

    /** 记录一条带事务状态的事件 */
    public Event record(String phase, String message, String txState) {
        Event event = new Event(sequencer.incrementAndGet(), System.nanoTime(), phase, message, txState);
        synchronized (events) {
            if (events.size() < MAX_EVENTS) {
                events.add(event);
            }
        }
        log.debug("TRACE | {}", event.render());
        return event;
    }

    /** 当前事件快照（副本） */
    public List<Event> snapshot() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    /** 当前事件的描述文本列表，按真实执行顺序 */
    public List<String> messages() {
        return snapshot().stream().map(Event::message).toList();
    }

    /** 是否出现过包含指定片段的事件 */
    public boolean containsText(String fragment) {
        return indexOfText(fragment) >= 0;
    }

    /** 返回第一个包含指定片段的事件在列表中的下标，找不到返回 -1 */
    public int indexOfText(String fragment) {
        List<Event> copy = snapshot();
        for (int i = 0; i < copy.size(); i++) {
            if (copy.get(i).message().contains(fragment)) {
                return i;
            }
        }
        return -1;
    }

    /** 最近一条包含指定片段的事件，找不到返回 null */
    public Event lastEventContaining(String fragment) {
        List<Event> copy = snapshot();
        for (int i = copy.size() - 1; i >= 0; i--) {
            if (copy.get(i).message().contains(fragment)) {
                return copy.get(i);
            }
        }
        return null;
    }

    public String runName() {
        return runName;
    }

    /** 把整条轨迹渲染成可直接阅读的文本 */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n================ 真实执行轨迹：").append(runName).append(" ================\n");
        snapshot().forEach(e -> sb.append(e.render()).append('\n'));
        sb.append("==================================================================\n");
        return sb.toString();
    }

    /** 把整条轨迹打到应用日志里（便于从控制台直接取证） */
    public void dump() {
        log.info("{}", render());
    }
}
