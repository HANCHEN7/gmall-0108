package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.config.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.index.utils.DistributedLock;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private DistributedLock lock;

    private static final String KEY_PREFIX = "index:cates:";
    private static final String LOCK_PREFIX = "index:cates:lock:";

    public List<CategoryEntity> queryLvl1Categories() {
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoriesByPid(0l);
        List<CategoryEntity> categoryEntities = listResponseVo.getData();
        return categoryEntities;
    }


    @GmallCache(prefix = KEY_PREFIX, timeout = 259200, random = 14400, lock = LOCK_PREFIX)
    public List<CategoryEntity> queryLvl2WithSubsByPid(Long pid) {
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoriesWithSub(pid);
        return listResponseVo.getData();
    }

    public List<CategoryEntity> queryLvl2WithSubsByPid2(Long pid) {
        String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isNotBlank(json)){
            return JSON.parseArray(json,CategoryEntity.class);
        }
        // 为了防止缓存击穿添加分布式锁
        RLock fairLock = this.redissonClient.getFairLock(LOCK_PREFIX + pid);
        fairLock.lock();

        try {
            // 在当前请求获取锁的过程中，可能已经有其他线程获取到锁，并把数据放入缓存，此时最好再次确认缓存中是否已有
            String json2 = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
            if (StringUtils.isNotBlank(json2)) {
                return JSON.parseArray(json2, CategoryEntity.class);
            }
            ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoriesWithSub(pid);
            List<CategoryEntity> categoryEntities = listResponseVo.getData();
            if (CollectionUtils.isEmpty(categoryEntities)){
                this.redisTemplate.opsForValue().set(KEY_PREFIX + pid,JSON.toJSONString(categoryEntities),5, TimeUnit.MINUTES);
            }else {
                this.redisTemplate.opsForValue().set(KEY_PREFIX + pid,JSON.toJSONString(categoryEntities),180+ new Random().nextInt(10), TimeUnit.DAYS);
            }
            return categoryEntities;
        } finally {
            fairLock.unlock();
        }
    }

    public void testLock() {

        RLock lock = this.redissonClient.getLock("lock");
        lock.lock();
        try {
            String numString = this.redisTemplate.opsForValue().get("num");
            if (StringUtils.isBlank(numString)){
                this.redisTemplate.opsForValue().set("num","1");
            }
            int num = Integer.parseInt(numString);
            this.redisTemplate.opsForValue().set("num",String.valueOf(++num));

            try {
                TimeUnit.SECONDS.sleep(1000);
            }catch (InterruptedException e){
                e.printStackTrace();
            }
        } finally {
            lock.unlock();
        }


    }


    public void testLock3() {

        String uuid = UUID.randomUUID().toString();

        Boolean flag = this.lock.lock("lock", uuid, 30);
        if (flag){

            String numString = this.redisTemplate.opsForValue().get("num");
            if (StringUtils.isBlank(numString)){
                this.redisTemplate.opsForValue().set("num","1");
            }
            int num = Integer.parseInt(numString);
            this.redisTemplate.opsForValue().set("num",String.valueOf(++num));

            //this.testSub("lock",uuid);
            try {
                TimeUnit.SECONDS.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            this.lock.unLock("lock",uuid);
        }

    }

    public void testSub(String lockName, String uuid) {
        this.lock.lock(lockName, uuid, 30);
        System.out.println("测试可重入锁");
        this.lock.unLock(lockName,uuid);

    }
    public void testLock2() {

        String uuId = UUID.randomUUID().toString();
        Boolean flag = this.redisTemplate.opsForValue().setIfAbsent("lock", uuId,3,TimeUnit.SECONDS);
        if (!flag){
            try {
                Thread.sleep(100);
                testLock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }else {
            String numString = this.redisTemplate.opsForValue().get("num");
            if (StringUtils.isBlank(numString)){
                this.redisTemplate.opsForValue().set("num","1");
            }
            int num = Integer.parseInt(numString);
            this.redisTemplate.opsForValue().set("num",String.valueOf(++num));

            String script = "if(redis.call('get',KEYS[1]) == ARGV[1]) then return redis.call('del',KEYS[1]) else return 0 end";
            this.redisTemplate.execute(new DefaultRedisScript<>(script,Boolean.class), Arrays.asList("lock"),uuId);
          /*  if (StringUtils.equals(uuId,this.redisTemplate.opsForValue().get("lock"))){
                this.redisTemplate.delete("lock");
            }*/
        }

    }

    public String readLock() {
        // 初始化读写锁
        RReadWriteLock rwLock = redissonClient.getReadWriteLock("rwLock");
        rwLock.readLock().lock(10,TimeUnit.SECONDS);

        String msg = this.redisTemplate.opsForValue().get("msg");

        //rwLock.readLock().unlock(); // 解锁
        return msg;
    }

    public String writeLock() {
        // 初始化读写锁
        RReadWriteLock rwLock = redissonClient.getReadWriteLock("rwLock");
        rwLock.writeLock().lock(10,TimeUnit.SECONDS);

        this.redisTemplate.opsForValue().set("msg", UUID.randomUUID().toString());

        //rwLock.writeLock().unlock(); // 解锁
        return "成功写入了内容。。。。。。";
    }


    public String latch() {
        RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("countdown");
        try {
            countDownLatch.trySetCount(6);
            countDownLatch.await();

            return "关门了。。。。。";
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String countDown() {
        RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("countdown");

        countDownLatch.countDown();
        return "出来了一个人。。。";
    }
}

