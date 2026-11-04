-- =====================================================================================
--  秒杀库存原子预扣脚本
--  KEYS[1] = seckill:stock:{productId}
--  ARGV[1] = 购买数量
--  返回值：
--    -1  → 库存未预热（业务侧降级为「只走数据库条件更新」）
--    -2  → 库存不足（快速失败，直接告诉用户"已售罄"，不打数据库）
--    >=0 → 扣减后剩余库存
--  说明：Lua 脚本在 Redis 内单线程原子执行，GET + 比较 + DECRBY 三步不可被打断，
--        这是「不用分布式锁也能防超卖」的标准做法。
-- =====================================================================================
local key = KEYS[1]
local quantity = tonumber(ARGV[1])

local stock = redis.call('GET', key)
if not stock then
    return -1
end

local available = tonumber(stock)
if available < quantity then
    return -2
end

return redis.call('DECRBY', key, quantity)
