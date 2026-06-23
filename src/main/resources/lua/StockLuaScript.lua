local stock = redis.call('GET', KEYS[1])
if stock == false then
    return -1
end
if tonumber(stock) < tonumber(ARGV[1]) then
    return 0
end
redis.call('DECRBY', KEYS[1], ARGV[1])
return 1