package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.cart.mapper.CartMapper;
import com.atguigu.gmall.cart.pojo.Cart;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CartAsyncService {

    @Autowired
    private CartMapper cartMapper;

    @Async
    public void insertCart(Cart cart) {
        this.cartMapper.insert(cart);
    }

    @Async
    public void updateCart(Cart cart,String userId,String skuId){
        this.cartMapper.update(cart,new UpdateWrapper<Cart>().eq("user_id",userId).eq("sku_id",skuId));
    }

    @Async
    public void deleteCartByUserId(String userId) {
        this.cartMapper.delete(new UpdateWrapper<Cart>().eq("user_id",userId));
    }

    @Async
    public void deleteCartByUserIdAndSkuId(String userId, Long skuId) {
        this.cartMapper.delete(new UpdateWrapper<Cart>().eq("user_id",userId).eq("sku_id",skuId));
    }
}












