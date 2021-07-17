package com.atguigu.gmall.cart.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.mapper.CartMapper;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.pojo.UserInfo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.CartException;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.concurrent.ListenableFuture;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CartService {

    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallSmsClient smsClient;
    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CartAsyncService asyncService;


    private static final String key_prefix = "cart:info:";
    private static final String price_prefix = "cart:price:";

    public void addCart(Cart cart) {

        String userId = getUserId();

        BoundHashOperations<String, Object, Object> boundHashOps = this.redisTemplate.boundHashOps(key_prefix + userId);

        BigDecimal count = cart.getCount();
        String skuId = cart.getSkuId().toString();
        if (boundHashOps.hasKey(skuId)) {

            String cartJson = boundHashOps.get(skuId).toString();
            cart = JSON.parseObject(cartJson,cart.getClass());
            cart.setCount(cart.getCount().add(count));

            this.asyncService.updateCart(cart,userId,skuId);

        }else {
            //新增购物车记录

            cart.setUserId(userId);

            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity ==null) {
                throw new CartException("你要加入的商品不存在！！！");
            }
            cart.setDefaultImage(skuEntity.getDefaultImage());
            cart.setPrice(skuEntity.getPrice());
            cart.setTitle(skuEntity.getTitle());

            ResponseVo<List<ItemSaleVo>> salesResponseVo = this.smsClient.queryItemSalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> itemSaleVos = salesResponseVo.getData();
            cart.setSales(JSON.toJSONString(itemSaleVos));

            ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = this.pmsClient.querySkuAttrValuesBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo.getData();
            cart.setSaleAttrs(JSON.toJSONString(skuAttrValueEntities));

            ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkuBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                cart.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }

            cart.setCheck(true);

            //写入数据库
            this.asyncService.insertCart(cart);

            //实时价格放入缓存
            this.redisTemplate.opsForValue().set(price_prefix + skuId,skuEntity.getPrice().toString());

        }
        boundHashOps.put(skuId,JSON.toJSONString(cart));

    }



    private String getUserId() {
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String userId = userInfo.getUserKey();
        if (userInfo.getUserId() != null) {
            userId = userInfo.getUserId().toString();
        }
        return userId;
    }

    public Cart queryCartBySkuId(Cart cart) {

        String userId = this.getUserId();

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key_prefix + userId);
        if (!hashOps.hasKey(cart.getSkuId().toString())) {
            throw new CartException("当前用户没有对应的购物车记录");
        }
        String cartJson = hashOps.get(cart.getSkuId().toString()).toString();
        return JSON.parseObject(cartJson, Cart.class);
    }

    @Async
    public void executor1(){
        try {
            System.out.println("executor1开始执行了-------");
            TimeUnit.SECONDS.sleep(4);
            System.out.println("executor1执行结束了----==========----");
        } catch (InterruptedException e) {
            //return AsyncResult.forExecutionException(e);
            e.printStackTrace();
        }
        //return AsyncResult.forValue("12345678");
    }
    @Async
    public void executor2(){
        try {
            System.out.println("executor2开始执行了-------");
            TimeUnit.SECONDS.sleep(5);
            int i = 1/0;
            System.out.println("executor2执行结束了----==========----");
        } catch (InterruptedException e) {
            //return AsyncResult.forExecutionException(e);
            e.printStackTrace();
        }
        //return AsyncResult.forValue("87654321");
    }

    public List<Cart> queryCarts() {

        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String userKey = userInfo.getUserKey();
        //获取未登录状态购物车
        BoundHashOperations<String, Object, Object> unloginHashOps = this.redisTemplate.boundHashOps(key_prefix + userKey);
        List<Object> unloginCartJsons = unloginHashOps.values();
        List<Cart> unloginCarts = null;
        if (!CollectionUtils.isEmpty(unloginCartJsons)) {
            unloginCarts = unloginCartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(price_prefix + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
        }

        Long userId = userInfo.getUserId();
        if (userId == null) {
            return unloginCarts;
        }

        //获取登录状态购物车
        BoundHashOperations<String, Object, Object> loginHashOps = this.redisTemplate.boundHashOps(key_prefix + userId);
        if (!CollectionUtils.isEmpty(unloginCarts)) {
            unloginCarts.forEach(cart -> {
                String skuId = cart.getSkuId().toString();
                BigDecimal count = cart.getCount();
                if (loginHashOps.hasKey(skuId)) {
                    String cartJson = loginHashOps.get(skuId).toString();
                    cart = JSON.parseObject(cartJson, Cart.class);
                    cart.setCount(cart.getCount().add(count));

                    this.asyncService.updateCart(cart,userId.toString(),skuId);
                }else {
                    cart.setUserId(userId.toString());
                    this.asyncService.insertCart(cart);
                }
                loginHashOps.put(skuId,JSON.toJSONString(cart));

            });

            this.redisTemplate.delete(key_prefix + userKey);
            this.asyncService.deleteCartByUserId(userKey);

        }

        List<Object> loginCartJsons = loginHashOps.values();
        if (!CollectionUtils.isEmpty(loginCartJsons)) {
            return loginCartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(),Cart.class);
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(price_prefix + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
        }

        return null;
    }

    public void updateNum(Cart cart) {
        String userId = this.getUserId();

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key_prefix + userId);
        if (!hashOps.hasKey(cart.getSkuId().toString())) {
            throw new CartException("没有对应的商品记录");
        }
        BigDecimal count = cart.getCount();

        String cartJson = hashOps.get(cart.getSkuId().toString()).toString();
        cart = JSON.parseObject(cartJson, Cart.class);
        cart.setCount(count);

        hashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
        this.asyncService.updateCart(cart,userId,cart.getSkuId().toString());

    }

    public void updateStatus(Cart cart) {
        String userId = this.getUserId();

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key_prefix + userId);
        if (!hashOps.hasKey(cart.getSkuId().toString())) {
            throw new CartException("没有对应的商品记录");
        }
        Boolean check = cart.getCheck();

        String cartJson = hashOps.get(cart.getSkuId().toString()).toString();
        cart = JSON.parseObject(cartJson, Cart.class);
        cart.setCheck(check);

        hashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
        this.asyncService.updateCart(cart,userId,cart.getSkuId().toString());
    }

    public void deleteCart(Long skuId) {
        String userId = this.getUserId();

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key_prefix + userId);

        hashOps.delete(skuId.toString());
        this.asyncService.deleteCartByUserIdAndSkuId(userId,skuId);
    }
}
















