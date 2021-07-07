package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String key_prefix ="index:cates:";

    public List<CategoryEntity> queryLvl1Categories() {
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoryByPid(0l);
        List<CategoryEntity> categoryEntities = listResponseVo.getData();
        return categoryEntities;
    }

    public List<CategoryEntity> queryLvl2WithSubsByPid(Long pid) {
        String json = this.redisTemplate.opsForValue().get(key_prefix + pid);
        if (StringUtils.isNotBlank(json)){
            return JSON.parseArray(json,CategoryEntity.class);
        }
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoriesWithSub(pid);
        List<CategoryEntity> categoryEntities = listResponseVo.getData();
        if (CollectionUtils.isEmpty(categoryEntities)){
            this.redisTemplate.opsForValue().set(key_prefix + pid,JSON.toJSONString(categoryEntities),5, TimeUnit.SECONDS);
        }else {
            this.redisTemplate.opsForValue().set(key_prefix + pid,JSON.toJSONString(categoryEntities),180+ new Random().nextInt(10), TimeUnit.DAYS);
        }
        return categoryEntities;
    }
}
