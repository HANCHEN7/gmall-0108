package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.bean.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Controller
public class CartController {

    @Autowired
    private CartService cartService;

    @GetMapping
    public String addCart(Cart cart){

        this.cartService.addCart(cart);
        return "redirect:http://cart.gmall.com/addCart.html?skuId=" + cart.getSkuId() + "&count=" + cart.getCount();
    }

    @GetMapping("addCart.html")
    public String queryCartBySkuId(Cart cart, Model model){

        BigDecimal count = cart.getCount();
        cart = this.cartService.queryCartBySkuId(cart);
        cart.setCount(count);
        model.addAttribute("cart",cart);
        return "addCart";
    }

    @GetMapping("cart.html")
    public String queryCarts(Model model){
        List<Cart> carts = this.cartService.queryCarts();
        model.addAttribute("carts",carts);
        return "cart";
    }

    @PostMapping("updateNum")
    @ResponseBody
    public ResponseVo updateNum(@RequestBody Cart cart){
        this.cartService.updateNum(cart);
        return ResponseVo.ok();
    }

    @PostMapping("updateStatus")
    @ResponseBody
    public ResponseVo updateStatus(@RequestBody Cart cart){
        this.cartService.updateStatus(cart);
        return ResponseVo.ok();
    }
    @PostMapping("deleteCart")
    @ResponseBody
    public ResponseVo deleteCart(@RequestParam("skuId")Long skuId){
        this.cartService.deleteCart(skuId);
        return ResponseVo.ok();
    }


    @GetMapping("test")
    @ResponseBody
    public String test(HttpServletRequest request) throws ExecutionException, InterruptedException {

        System.out.println(LoginInterceptor.getUserInfo());
        long now = System.currentTimeMillis();
        System.out.println("controller?????????????????????=======+++++++");
//        ListenableFuture<String> future1 = this.cartService.executor1();
//        ListenableFuture<String> future2 = this.cartService.executor2();
        this.cartService.executor1();
        this.cartService.executor2();
        //System.out.println(future1.get() + "!!!!!!!!!" + future2.get());
//        future1.addCallback(result -> {
//            System.out.println("executor1????????????" + result);
//        },ex -> {
//            System.out.println("executor1????????????" + ex.getMessage());
//
//        });
//        future2.addCallback(result -> {
//            System.out.println("executor2????????????" + result);
//        },ex -> {
//            System.out.println("executor2????????????" + ex.getMessage());
//
//        });
        System.out.println("controller?????????????????????=======+++++++"+ (System.currentTimeMillis() - now));

        return "test";
    }



}
