package com.giving.util;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RedisUtils {
	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	/**
	 * 保证list长度，更新最新数据
	 * @param key
	 * @param value
	 */
	public void addToRecentList(String key, List value, Long size) {
		// 使用leftPushAll方法一次性将多个值推入到列表的头部
		redisTemplate.opsForList().leftPushAll(key, value);
		// 使用LTRIM保持列表只有最新的设定长度数据
		redisTemplate.opsForList().trim(key, 0, size - 1);
	}

	/**
	 * 添加最新数据到列表，并保证列表长度不超过设定的大小
	 * @param key 列表的键名
	 * @param value 要添加的值
	 * @param size 列表的最大长度
	 */
	public void addToRecent(String key, Object value, Long size) {
		// 将值推入到列表的头部
		redisTemplate.opsForList().leftPush(key, value);
		// 保持列表只有最新的设定长度数据
		redisTemplate.opsForList().trim(key, 0, size - 1);
	}


	/**
	 * 批量存放LIST类型数据
	 * 
	 * @author yangxy
	 * @version 创建时间：2023年2月10日 下午2:22:12
	 * @param key
	 * @param value
	 * @return
	 */
	public boolean pushAllList(String key, List list) {
		try {
			redisTemplate.opsForList().rightPushAll(key, list);
			return true;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
	/**
	 * 
	* @author yangxy
	* @version 创建时间：2023年9月28日 下午2:24:38 
	* @param map
	* @param expire
	 */
	public void batchAddList(Map<String,Object> map,int expire) {
		redisTemplate.executePipelined(new SessionCallback<Object>() {
		    @Override
		    public Object execute(RedisOperations redisOperations) throws DataAccessException {
		    	map.forEach((key, item) -> {
					pushList(key, item);
					if(expire > 0) {
						expire(key, expire);
					}
					
				});
		        return null;
		    }
		});
	}

	/**
	 * 获取LIST类型数据总数
	 * 
	 * @author yangxy
	 * @version 创建时间：2023年9月25日 上午11:56:40
	 * @param key
	 * @return
	 */
	public int getListSize(String key) {
		try {
			Long size = redisTemplate.opsForList().size(key);
			return size.intValue();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

	/**
	 * redis存放list类型数据
	 * 
	 * @author yangxy
	 * @version 创建时间：2023年2月10日 下午2:22:12
	 * @param key
	 * @param value
	 * @return
	 */
	public boolean pushList(String key, Object value) {
		try {
			redisTemplate.opsForList().rightPush(key, value);
			return true;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * 获取指定位置值
	 * 
	 * @author yangxy
	 * @version 创建时间：2023年2月10日 下午2:23:19
	 * @param key
	 * @param index
	 * @return
	 */
	public Object getList(String key, int index) {
		try {
			Object index2 = redisTemplate.opsForList().index(key, index);
			return index2;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * 删除list数据第一个和数据相同的值，并且如果list长度为0删除key
	 * 
	 * @author yangxy
	 * @version 创建时间：2023年2月17日 上午10:48:55
	 * @param key
	 * @param value
	 */
	public Long removeList(String key, Object value) {
		Long remove = redisTemplate.opsForList().remove(key, 0, value);
		Long size = redisTemplate.opsForList().size(key);
		if (size == 0) {
			this.del(key);
		}
		return remove;
	}

	/**
	 * 获取所有list数据
	 * 
	 * @author yangxy
	 * @version 创建时间：2023年2月10日 下午2:22:03
	 * @param key
	 * @return
	 */
	public List<Object> getAllList(String key) {
		List<Object> list = redisTemplate.opsForList().range(key, 0, -1);
		return list;
	}

	/**
	 * 分页获取list类型数据
	 * 
	 * @author yangxy
	 * @version 创建时间：2023年9月25日 上午11:07:50
	 * @param key
	 * @param pageNo
	 * @param pageSize
	 * @return
	 */
	public List<Object> getListPage(String key, int pageNo, int pageSize) {
		int start = (pageNo - 1) * pageSize;
		int end = start + pageSize - 1;
		List<Object> list = redisTemplate.opsForList().range(key, start, end);
		return list;
	}

	/**
	 * redis分布式锁
	 * 
	 * @author yangxy
	 * @version 创建时间：2023年1月11日 下午5:03:54
	 * @param key
	 * @param value
	 * @param time
	 * @return
	 */
	public boolean setLock(String key, Object value, long time) {
		try {
			Boolean setIfAbsent = redisTemplate.opsForValue().setIfAbsent(key, value, time, TimeUnit.SECONDS);
			return setIfAbsent;
		} catch (Exception e) {
			// TODO: handle exception
			log.error("redis失败:{}", e);
			return false;
		}
	}

	/**
	 * 指定缓存失效时间
	 * 
	 * @param key  键
	 * @param time 时间(秒)
	 * @return
	 */
	public boolean expire(String key, long time) {
        try {
            if(time>0){
                redisTemplate.expire(key, time, TimeUnit.SECONDS);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
		return false;
	}

	/**
	 * 获取redis中指定前缀的所有key
	 * 
	 * @author yangxy
	 * @version 创建时间：2023年4月10日 上午10:12:30
	 * @param str1
	 * @return
	 */
	public List<String> getRedis(String str1) {
		// 获取所有的key
		Set<String> keys = redisTemplate.keys(str1 + "*");
		List<String> list = new ArrayList<>();
		for (String key : keys) {
			list.add(key);
		}
		return list;
	}

	/**
	 * 根据key 获取过期时间
	 * 
	 * @param key 键 不能为null
	 * @return 时间(秒) 返回0代表为永久有效
	 */
	public long getExpire(String key) {
		return redisTemplate.getExpire(key, TimeUnit.SECONDS);
	}

	/**
	 * 判断key是否存在
	 * 
	 * @param key 键
	 * @return true 存在 false不存在
	 */
	public boolean hasKey(String key) {
		try {
			return redisTemplate.hasKey(key);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 删除缓存
	 * 
	 * @param key 可以传一个值 或多个
	 */
	@SuppressWarnings("unchecked")
	public void del(String... key) {
		if (key != null && key.length > 0) {
			if (key.length == 1) {
				redisTemplate.delete(key[0]);
			} else {
				redisTemplate.delete(CollectionUtils.arrayToList(key));
			}
		}
	}

	// ============================String=============================
	/**
	 * 普通缓存获取
	 * 
	 * @param key 键
	 * @return 值
	 */
	public Object get(String key) {

		if (!StringUtils.isEmpty(key)) {
			Object obj = redisTemplate.opsForValue().get(key);

			int i = 0;
			while (obj == null && i < 5) {
				obj = redisTemplate.opsForValue().get(key);
				i++;
			}

			return obj;
		}

		return null;
	}

	/**
	 * 普通缓存放入
	 * 
	 * @param key   键
	 * @param value 值
	 * @return true成功 false失败
	 */
	public boolean set(String key, Object value) {
		try {
			this.del(key);
			redisTemplate.opsForValue().set(key, value);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

	}
	/**
	 * 批量添加zset数据
	 * 
	 * @author yangxy
	 * @version 创建时间：2023年9月28日 上午10:27:28
	 * @param key
	 * @param map 添加元素（key 元素名称，vlaue 分数）
	 */
	public void batchSet(Map<String, Object> map, long time) {
		redisTemplate.executePipelined(new RedisCallback<String>() {
			public String doInRedis(RedisConnection connection) {
				map.forEach((key, item) -> {
					if (time > 0) {
						redisTemplate.opsForValue().set(key, item, time, TimeUnit.SECONDS);
					} else {
						set(key, item);
					}
				});
				return null;
			}
		});
	}
	/**
	 * 普通缓存放入并设置时间
	 * 
	 * @param key   键
	 * @param value 值
	 * @param time  时间(秒) time要大于0 如果time小于等于0 将设置无限期
	 * @return true成功 false 失败
	 */
	public boolean set(String key, Object value, long time) {
		try {
			if (time > 0) {
				redisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);
			} else {
				set(key, value);
			}
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 递增
	 * 
	 * @param key 键
	 * @param by  要增加几(大于0)
	 * @return
	 */
	public long incr(String key, long delta) {
		if (delta < 0) {
			throw new RuntimeException("递增因子必须大于0");
		}
		return redisTemplate.opsForValue().increment(key, delta);
	}

	/**
	 * 递减
	 * 
	 * @param key 键
	 * @param by  要减少几(小于0)
	 * @return
	 */
	public long decr(String key, long delta) {
		if (delta < 0) {
			throw new RuntimeException("递减因子必须大于0");
		}
		return redisTemplate.opsForValue().increment(key, -delta);
	}

	// ================================Map=================================
	/**
	 * HashGet
	 * 
	 * @param key  键 不能为null
	 * @param item 项 不能为null
	 * @return 值
	 */
	public Object hget(String key, String item) {
		return redisTemplate.opsForHash().get(key, item);
	}

	/**
	 * 获取hashKey对应的所有键值
	 * 
	 * @param key 键
	 * @return 对应的多个键值
	 */
	public Map<Object, Object> hmget(String key) {
		return redisTemplate.opsForHash().entries(key);
	}

	/**
	 * HashSet
	 * 
	 * @param key 键
	 * @param map 对应多个键值
	 * @return true 成功 false 失败
	 */
	public boolean hmset(String key, Map<String, Object> map) {
		try {
			redisTemplate.opsForHash().putAll(key, map);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * HashSet 并设置时间
	 * 
	 * @param key  键
	 * @param map  对应多个键值
	 * @param time 时间(秒)
	 * @return true成功 false失败
	 */
	public boolean hmset(String key, Map<String, Object> map, long time) {
		try {
			redisTemplate.opsForHash().putAll(key, map);
			if (time > 0) {
				expire(key, time);
			}
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 向一张hash表中放入数据,如果不存在将创建
	 * 
	 * @param key   键
	 * @param item  项
	 * @param value 值
	 * @return true 成功 false失败
	 */
	public boolean hset(String key, String item, Object value) {
		try {
			redisTemplate.opsForHash().put(key, item, value);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 向一张hash表中放入数据,如果不存在将创建
	 * 
	 * @param key   键
	 * @param item  项
	 * @param value 值
	 * @param time  时间(秒) 注意:如果已存在的hash表有时间,这里将会替换原有的时间
	 * @return true 成功 false失败
	 */
	public boolean hset(String key, String item, Object value, long time) {
		try {
			redisTemplate.opsForHash().put(key, item, value);
			if (time > 0) {
				expire(key, time);
			}
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 删除hash表中的值
	 * 
	 * @param key  键 不能为null
	 * @param item 项 可以使多个 不能为null
	 */
	public void hdel(String key, Object... item) {
		redisTemplate.opsForHash().delete(key, item);
	}

	/**
	 * 判断hash表中是否有该项的值
	 * 
	 * @param key  键 不能为null
	 * @param item 项 不能为null
	 * @return true 存在 false不存在
	 */
	public boolean hHasKey(String key, String item) {
		return redisTemplate.opsForHash().hasKey(key, item);
	}

	/**
	 * hash递增 如果不存在,就会创建一个 并把新增后的值返回
	 * 
	 * @param key  键
	 * @param item 项
	 * @param by   要增加几(大于0)
	 * @return
	 */
	public double hincr(String key, String item, double by) {
		return redisTemplate.opsForHash().increment(key, item, by);
	}

	/**
	 * hash递减
	 * 
	 * @param key  键
	 * @param item 项
	 * @param by   要减少记(小于0)
	 * @return
	 */
	public double hdecr(String key, String item, double by) {
		return redisTemplate.opsForHash().increment(key, item, -by);
	}

	// ============================set=============================
	/**
	 * 根据key获取Set中的所有值
	 * 
	 * @param key 键
	 * @return
	 */
	public Set<Object> sGet(String key) {
		try {
			return redisTemplate.opsForSet().members(key);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * 根据value从一个set中查询,是否存在
	 * 
	 * @param key   键
	 * @param value 值
	 * @return true 存在 false不存在
	 */
	public boolean sHasKey(String key, Object value) {
		try {
			return redisTemplate.opsForSet().isMember(key, value);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 将数据放入set缓存
	 * 
	 * @param key    键
	 * @param values 值 可以是多个
	 * @return 成功个数
	 */
	public long sSet(String key, Object... values) {
		try {
			return redisTemplate.opsForSet().add(key, values);
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	/**
	 * 将set数据放入缓存
	 * 
	 * @param key    键
	 * @param time   时间(秒)
	 * @param values 值 可以是多个
	 * @return 成功个数
	 */
	public long sSetAndTime(String key, long time, Object... values) {
		try {
			Long count = redisTemplate.opsForSet().add(key, values);
			if (time > 0)
				expire(key, time);
			return count;
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	/**
	 * 获取set缓存的长度
	 * 
	 * @param key 键
	 * @return
	 */
	public long sGetSetSize(String key) {
		try {
			return redisTemplate.opsForSet().size(key);
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	/**
	 * 移除值为value的
	 * 
	 * @param key    键
	 * @param values 值 可以是多个
	 * @return 移除的个数
	 */
	public long setRemove(String key, Object... values) {
		try {
			Long count = redisTemplate.opsForSet().remove(key, values);
			return count;
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}
	// ===============================list=================================

	/**
	 * 获取list缓存的内容
	 * 
	 * @param key   键
	 * @param start 开始
	 * @param end   结束 0 到 -1代表所有值
	 * @return
	 */
	public List<Object> lGet(String key, long start, long end) {
		try {
			return redisTemplate.opsForList().range(key, start, end);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * 获取list缓存的长度
	 * 
	 * @param key 键
	 * @return
	 */
	public long lGetListSize(String key) {
		try {
			return redisTemplate.opsForList().size(key);
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	/**
	 * 通过索引 获取list中的值
	 * 
	 * @param key   键
	 * @param index 索引 index>=0时， 0 表头，1 第二个元素，依次类推；index<0时，-1，表尾，-2倒数第二个元素，依次类推
	 * @return
	 */
	public Object lGetIndex(String key, long index) {
		try {
			return redisTemplate.opsForList().index(key, index);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * 将list放入缓存
	 * 
	 * @param key   键
	 * @param value 值
	 * @param time  时间(秒)
	 * @return
	 */
	public boolean lSet(String key, Object value) {
		try {
			redisTemplate.opsForList().rightPush(key, value);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 将list放入缓存
	 * 
	 * @param key   键
	 * @param value 值
	 * @param time  时间(秒)
	 * @return
	 */
	public boolean lSet(String key, Object value, long time) {
		try {
			redisTemplate.opsForList().rightPush(key, value);
			if (time > 0)
				expire(key, time);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 将list放入缓存
	 * 
	 * @param key   键
	 * @param value 值
	 * @param time  时间(秒)
	 * @return
	 */
	public boolean lSet(String key, List<Object> value) {
		try {
			redisTemplate.opsForList().rightPushAll(key, value);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 将list放入缓存
	 * 
	 * @param key   键
	 * @param value 值
	 * @param time  时间(秒)
	 * @return
	 */
	public boolean lSet(String key, List<Object> value, long time) {
		try {
			redisTemplate.opsForList().rightPushAll(key, value);
			if (time > 0)
				expire(key, time);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 根据索引修改list中的某条数据
	 * 
	 * @param key   键
	 * @param index 索引
	 * @param value 值
	 * @return
	 */
	public boolean lUpdateIndex(String key, long index, Object value) {
		try {
			redisTemplate.opsForList().set(key, index, value);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 移除N个值为value
	 * 
	 * @param key   键
	 * @param count 移除多少个
	 * @param value 值
	 * @return 移除的个数
	 */
	public long lRemove(String key, long count, Object value) {
		try {
			return redisTemplate.opsForList().remove(key, count, value);
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	/**
	 * Redis加锁的操作
	 *
	 * @param key  键
	 * @param time 过期时间(秒)
	 * @return false 加锁失败 true加锁成功
	 */
	public Boolean tryLock(String key, long time) {
		int tryConut = 0;
//    	String loginId = ThreadLocalUtil.getCurrentUser().getLoginId();
		String loginId = "loginId";
		boolean flag = false;
		while (tryConut < 3) {// 三次加锁尝试
			tryConut++;
			if (!this.hasKey(key)) {// 锁不存在
				this.set(key, loginId, time);
				flag = true;
				break;
			}
			try {
				tryConut++;
				Thread.sleep(300);// 休眠300毫秒
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return flag;
	}

	/**
	 * Redis解锁的操作
	 *
	 * @param key 键
	 */
	public void unlock(String key) {
		if (this.hasKey(key)) {
			String value = (String) this.get(key);
//    		String loginId = ThreadLocalUtil.getCurrentUser().getLoginId();
			String loginId = "loginId";
			if (loginId.equals(value)) {// 判断当前用户是否为锁的拥有者
				this.del(key);
			}
		}
	}

	/*** zset(有序集合操作) ***/
	/**
	 * 添加一个元素, zset与set最大的区别就是每个元素都有一个score，因此有个排序的辅助功能; zadd
	 *
	 * @param key
	 * @param value 元素值
	 * @param score 得分
	 */
	public void addZSet(String key, String value, double score) {
		redisTemplate.opsForZSet().add(key, value, score);
	}

	/**
	 * 删除元素 zrem
	 *
	 * @param key
	 * @param value 元素值
	 */
	public void removeZset(String key, String value) {
		redisTemplate.opsForZSet().remove(key, value);
	}

	/**
	 * 增加key对应的集合中元素v1的score值，并返回增加后的值
	 *
	 * @param key
	 * @param value 元素值
	 * @param score
	 */
	public Double incrScore(String key, String value, double score) {
		return redisTemplate.opsForZSet().incrementScore(key, value, score);
	}

	/**
	 * 查询value对应的score 当value在集合中时，返回其score；如果不在，则返回null
	 *
	 * @param key
	 * @param value
	 * @return
	 */
	public Double score(String key, String value) {
		return redisTemplate.opsForZSet().score(key, value);
	}

	/**
	 * 判断value在zset中的排名 score越小排名越高
	 *
	 * @param key
	 * @param value
	 * @return
	 */
	public Long rank(String key, String value) {
		return redisTemplate.opsForZSet().rank(key, value);
	}

	/**
	 * 返回zset集合的长度
	 *
	 * @param key
	 * @return
	 */
	public Long size(String key) {
		return redisTemplate.opsForZSet().zCard(key);
	}

	/**
	 * 查询集合中指定顺序的值， 0 -1 表示获取全部的集合内容 zrange
	 *
	 * 返回有序的集合，score小的在前面
	 *
	 * @param key
	 * @param start
	 * @param end
	 * @return
	 */
	public List<String> range(String key, int start, int end) {
		List<String> list = Lists.newArrayList();
		Set<byte[]> zRange = redisTemplate.getConnectionFactory().getConnection().zSetCommands().zRange(key.getBytes(), start, end);
		for(byte[] by : zRange) {
			try {
				list.add(new String(by,"utf-8"));
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return list;
	}
	/**
	 * 批量添加zset数据
	 * 
	 * @author yangxy
	 * @version 创建时间：2023年9月28日 上午10:27:28
	 * @param key
	 * @param map 添加元素（key 元素名称，vlaue 分数）
	 */
	public void batchAddZset(String key, Map<String, Double> map) {
//		redisTemplate.executePipelined(new RedisCallback<String>() {
//			public String doInRedis(RedisConnection connection) {
//				map.forEach((item, score) -> {
////					addZSet(key, item, score);
//					connection.zSetCommands().zIncrBy(key.getBytes(), score, item.getBytes());
//				});
//				return null;
//			}
//		});
		ZSetOperations<String, Object> opsForZSet = redisTemplate.opsForZSet();
		for(Map.Entry<String, Double> entry : map.entrySet()) {
			opsForZSet.incrementScore(key, entry.getKey(),entry.getValue());
		}
//		Map<String,Map<String, String>> amqMap = Maps.newConcurrentMap();
//		Map<String, String> map1 = Maps.newConcurrentMap();
//		for(String key1:map.keySet()) {
//			map1.put(key1, String.valueOf(map.get(key1)));
//		}
//		amqMap.put(key, map1);
//		amqUtils.sendMqMsg(VndAmqEnums.SAVE_SOSRT_ZSET.exchangeName, VndAmqEnums.SAVE_SOSRT_ZSET.routeKey, amqMap);
	}
	
	/**
	 * 获取zset所有成员及分数
	* @author yangxy
	* @version 创建时间：2025年11月20日 下午12:51:40 
	* @param key
	* @return
	 */
	public Set<TypedTuple<Object>> getAllItemsAndScoresFromZSet(String key) {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        // 获取所有成员及其分数
        Set<TypedTuple<Object>> typedTuples = zSetOps.rangeWithScores(key, 0, -1);
        return typedTuples;
    }
	/**
	 * 查询集合中指定顺序的值和score，0, -1 表示获取全部的集合内容
	 *
	 * @param key
	 * @param start
	 * @param end
	 * @return
	 */
	public Set<TypedTuple<Object>> rangeWithScore(String key, int start, int end) {
		return redisTemplate.opsForZSet().rangeWithScores(key, start, end);
	}

	/**
	 * 查询集合中指定顺序的值 zrevrange
	 *
	 * 返回有序的集合中，score大的在前面
	 *
	 * @param key
	 * @param start
	 * @param end
	 * @return
	 */
	public Set<Object> revRange(String key, int start, int end) {
		return redisTemplate.opsForZSet().reverseRange(key, start, end);
	}

	/********** set数据操作 *************/
	/**
	 * set中增加元素，支持一次增加多个元素，逗号分隔即可，结果返回添加的个数
	 *
	 * @param key
	 * @param value
	 * @return
	 */
	public Long addSet(String key, Object... value) {
		Long size = null;
		try {
			size = redisTemplate.opsForSet().add(key, value);
		} catch (Exception e) {
			log.error("[RedisUtils.addSet] [error]", e);
			return size;
		}
		return size;
	}

	/**
	 * set中移除指定元素
	 *
	 * @param key
	 * @param value
	 * @return
	 */
	public Long removeSet(String key, Object value) {
		Long size = null;
		try {
			size = redisTemplate.opsForSet().remove(key, value);
		} catch (Exception e) {
			log.error("[RedisUtils.removeSet] [error]", e);
			return size;
		}
		return size;
	}
	
	public List<Object> getAllHash(String key) {
		List<Object> values = redisTemplate.opsForHash().values(key);
		return values;
	}

	/**
	 * 计算set集合大小
	 *
	 * @param key
	 * @return
	 */
	public Long countSet(String key) {
		Long size = null;
		try {
			size = redisTemplate.opsForSet().size(key);
		} catch (Exception e) {
			log.error("[RedisUtils.countSet] [error]", e);
		}
		return size;
	}

	/**
	 * 判断set中是否存在某元素
	 *
	 * @param key
	 * @param value
	 * @return
	 */
	public Boolean hasMemberSet(String key, Object value) {
		Boolean exist = false;
		try {
			exist = redisTemplate.opsForSet().isMember(key, value);
		} catch (Exception e) {
			log.error("[RedisUtils.hasNumberSet] [error]", e);
		}
		return exist;
	}

	/**
	 * 随机获取set中的一个元素
	 *
	 * @param key
	 * @return
	 */
	public Object randomMember(String key) {
		Object o = null;
		try {
			o = redisTemplate.opsForSet().randomMember(key);
		} catch (Exception e) {
			log.error("[RedisUtils.randomMember] [error]", e);
		}
		return o;
	}

	/**
	 * 获取key下的所有元素
	 *
	 * @param key
	 * @return
	 */
	public List<Object> getAllSet(String key) {
		Set<Object> result = null;
		try {
			result = redisTemplate.opsForSet().members(key);
		} catch (Exception e) {
			log.error("[RedisUtils.members] [error]", e);
		}
		List<Object> list = Lists.newArrayList();
		list.addAll(result);
		return list;
	}
}
