package com.example.txlab.trace;

/**
 * 某一瞬间、当前线程绑定的事务状态快照。
 *
 * <p>所有字段都来自 Spring 的公共 API，语义如下（Spring Framework 6.2.x）：</p>
 *
 * <ul>
 *   <li>{@code actualTransactionActive}：
 *       {@code TransactionSynchronizationManager.isActualTransactionActive()}。
 *       true 表示当前线程真的绑定了一个"实际事务"（物理连接 + 事务状态），
 *       而不只是有个事务同步集合。</li>
 *   <li>{@code synchronizationActive}：
 *       {@code TransactionSynchronizationManager.isSynchronizationActive()}。
 *       true 表示当前线程有一个事务同步集合，可以注册
 *       {@code TransactionSynchronization} 回调（beforeCommit / afterCompletion 等）。
 *       参与已有事务的内层方法也会是 true，但它并不会新建同步集合。</li>
 *   <li>{@code transactionName}：当前事务的名称，默认是
 *       {@code 包名.类名.方法名}，能直接看出"这条连接是被哪个方法开启的"。</li>
 *   <li>{@code boundResourceCount}：当前线程绑定的资源数量
 *       （{@code getResourceMap().size()}）。REQUIRES_NEW 挂起外层时会先解绑外层资源再绑定新连接。</li>
 *   <li>{@code newTransaction}：{@code TransactionStatus.isNewTransaction()}。
 *       外层 REQUIRED 首次开启时为 true；内层 REQUIRED 加入已有事务时为 false；
 *       内层 REQUIRES_NEW 为 true（独立物理事务）。</li>
 *   <li>{@code rollbackOnly}：{@code TransactionStatus.isRollbackOnly()}。
 *       true 表示当前事务已经被标记为"只能回滚、不能提交"。</li>
 * </ul>
 *
 * <p>说明：{@code newTransaction} / {@code rollbackOnly} 通过
 * {@code TransactionAspectSupport.currentTransactionStatus()} 获取，
 * 它返回的是"当前线程正在执行的这个 @Transactional 方法对应的状态"。
 * 方法体之外（或没有事务时）会抛 {@code NoTransactionException}，因此这里用 null 表示"读不到"。</p>
 */
public record TxSnapshot(
        boolean actualTransactionActive,
        boolean synchronizationActive,
        String transactionName,
        int boundResourceCount,
        Boolean newTransaction,
        Boolean rollbackOnly) {

    /** 渲染成紧凑的单行文本，便于写进轨迹 */
    public String toText() {
        return "tx{active=%s, sync=%s, newTx=%s, rollbackOnly=%s, resources=%d, name=%s}".formatted(
                actualTransactionActive,
                synchronizationActive,
                newTransaction,
                rollbackOnly,
                boundResourceCount,
                transactionName);
    }
}
