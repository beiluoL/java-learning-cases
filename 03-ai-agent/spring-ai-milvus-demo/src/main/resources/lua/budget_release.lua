-- 幂等释放：模型调用失败/被拒绝时归还所有作用域的预扣额度，不增加 used。
-- KEYS[1]=resvHash KEYS[2]=expireIndex KEYS[3+2i]=used_i KEYS[4+2i]=reserved_i
-- ARGV[1]=requestId [2]=finalStatus [3]=nowSec [4]=scopeCount
if redis.call('EXISTS', KEYS[1]) == 0 then
  return {0, 'NOT_FOUND', '0'}
end
local st = redis.call('HGET', KEYS[1], 'status')
if st ~= 'RESERVED' then
  return {0, 'ALREADY_' .. st, '0'}
end
local n = tonumber(ARGV[4])
local reserved = tonumber(redis.call('HGET', KEYS[1], 'amount') or '0')
for i = 1, n do
  redis.call('INCRBY', KEYS[2 + 2 * i], -reserved)
end
redis.call('HSET', KEYS[1], 'status', ARGV[2], 'settledAt', ARGV[3])
redis.call('ZREM', KEYS[2], ARGV[1])
return {1, 'RELEASED', tostring(reserved)}
