-- 幂等结算：仅 RESERVED 可推进；释放各作用域预扣、把实际成本计入 used。
-- 实际成本高于预扣时仍按实际计入，由 Java 侧写 SETTLE_OVERRUN 告警。
-- KEYS[1]=resvHash KEYS[2]=expireIndex KEYS[3+2i]=used_i KEYS[4+2i]=reserved_i
-- ARGV[1]=requestId [2]=actualAmount [3]=finalStatus [4]=nowSec [5]=scopeCount
if redis.call('EXISTS', KEYS[1]) == 0 then
  return {0, 'NOT_FOUND', '0'}
end
local st = redis.call('HGET', KEYS[1], 'status')
if st ~= 'RESERVED' then
  return {0, 'ALREADY_' .. st, '0'}
end
local n = tonumber(ARGV[5])
local reserved = tonumber(redis.call('HGET', KEYS[1], 'amount') or '0')
local actual = tonumber(ARGV[2])
for i = 1, n do
  redis.call('INCRBY', KEYS[2 + 2 * i], -reserved)
  redis.call('INCRBY', KEYS[2 + 2 * i - 1], actual)
end
redis.call('HSET', KEYS[1], 'status', ARGV[3], 'actual', ARGV[2], 'settledAt', ARGV[4])
redis.call('ZREM', KEYS[2], ARGV[1])
return {1, 'SETTLED', tostring(actual), tostring(reserved)}
