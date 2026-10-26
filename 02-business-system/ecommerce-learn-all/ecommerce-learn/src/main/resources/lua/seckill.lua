-- 秒杀原子扣减脚本
-- KEYS[1] = 库存 key（seckill:stock:{activityId}:{skuId}）
-- KEYS[2] = 已购用户集合 key（seckill:bought:{activityId}）
-- ARGV[1] = 购买数量
-- ARGV[2] = 用户标识（skuId:userId）
-- 返回值：1 成功；0 库存不足；-1 库存未初始化；-2 重复购买
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil then
  return -1
end
if stock < tonumber(ARGV[1]) then
  return 0
end
if redis.call('SISMEMBER', KEYS[2], ARGV[2]) == 1 then
  return -2
end
redis.call('DECRBY', KEYS[1], ARGV[1])
redis.call('SADD', KEYS[2], ARGV[2])
return 1
