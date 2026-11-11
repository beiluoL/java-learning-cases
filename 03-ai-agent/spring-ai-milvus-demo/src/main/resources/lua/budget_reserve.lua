-- 多作用域预算原子预扣。金额单位 = 1e-8 USD 整数，避免 Lua 浮点误差。
-- 所有 key 共用 {budget} hashtag，保证 Redis Cluster 下同槽。
-- KEYS[1]=resvHash  KEYS[2]=expireIndex  KEYS[3+2i]=used_i  KEYS[4+2i]=reserved_i (i 从 0 开始)
-- ARGV[1]=requestId [2]=amount [3]=nowSec [4]=ttlSec [5]=userId [6]=scopeCount
-- ARGV[7+3i]=limit_i [8+3i]=budgetType_i [9+3i]=targetId_i
if redis.call('EXISTS', KEYS[1]) == 1 then
  return {0, 'DUPLICATE', redis.call('HGET', KEYS[1], 'status') or ''}
end
local n = tonumber(ARGV[6])
local amount = tonumber(ARGV[2])
-- 第一遍：任一作用域额度不足即整体拒绝，不做任何扣减
for i = 1, n do
  local base = 7 + 3 * (i - 1)
  local limit = tonumber(ARGV[base])
  local used = tonumber(redis.call('GET', KEYS[2 + 2 * i - 1]) or '0')
  local held = tonumber(redis.call('GET', KEYS[2 + 2 * i]) or '0')
  if used + held + amount > limit then
    return {0, 'INSUFFICIENT', ARGV[base + 1] .. ':' .. ARGV[base + 2], tostring(limit - used - held)}
  end
end
-- 第二遍：全部通过后原子扣减（同一 Lua 内不会被其它命令插入）
for i = 1, n do
  redis.call('INCRBY', KEYS[2 + 2 * i], amount)
end
redis.call('HSET', KEYS[1],
  'requestId', ARGV[1], 'amount', ARGV[2], 'status', 'RESERVED',
  'scopes', ARGV[6], 'userId', ARGV[5], 'createdAt', ARGV[3])
for i = 1, n do
  local base = 7 + 3 * (i - 1)
  -- 把完整 used/reserved key 存进明细，供结算与过期清理精确还原（支持按日/月分周期 key）
  redis.call('HSET', KEYS[1], 'bt' .. i, ARGV[base + 1], 'tid' .. i, ARGV[base + 2],
             'uk' .. i, KEYS[2 + 2 * i - 1], 'rk' .. i, KEYS[2 + 2 * i])
end
redis.call('ZADD', KEYS[2], tonumber(ARGV[3]) + tonumber(ARGV[4]), ARGV[1])
return {1, 'RESERVED', tostring(n), tostring(amount)}
