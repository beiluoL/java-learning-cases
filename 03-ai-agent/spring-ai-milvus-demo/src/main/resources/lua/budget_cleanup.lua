-- 过期预扣清理：不能只靠 Key TTL，否则 reserved 总额不会同步扣减。
-- KEYS[1]=expireIndex
-- ARGV[1]=nowSec [2]=limit [3]=finalStatus
local expired = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, tonumber(ARGV[2]))
local cleaned = 0
for i, rid in ipairs(expired) do
  local rk = 'ai:{budget}:resv:' .. rid
  if redis.call('EXISTS', rk) == 1 and redis.call('HGET', rk, 'status') == 'RESERVED' then
    local scopes = tonumber(redis.call('HGET', rk, 'scopes') or '0')
    local amt = tonumber(redis.call('HGET', rk, 'amount') or '0')
    for j = 1, scopes do
      local heldKey = redis.call('HGET', rk, 'rk' .. j)
      if heldKey then
        redis.call('INCRBY', heldKey, -amt)
      end
    end
    redis.call('HSET', rk, 'status', ARGV[3], 'cleanedAt', ARGV[1])
    cleaned = cleaned + 1
  end
  redis.call('ZREM', KEYS[1], rid)
end
return {cleaned, #expired}
