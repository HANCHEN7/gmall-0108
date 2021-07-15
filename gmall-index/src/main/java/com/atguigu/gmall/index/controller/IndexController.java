package com.atguigu.gmall.index.controller;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.service.IndexService;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.xml.ws.Response;
import java.util.List;

@Controller
public class IndexController {

    @Autowired
    private IndexService indexService;

    @GetMapping
    public String toIndex(Model model){

        List<CategoryEntity> categoryEntityList = this.indexService.queryLvl1Categories();
        model.addAttribute("categories",categoryEntityList);
        return "index";
    }

    @ResponseBody
    @GetMapping("index/cates/{pid}")
    public ResponseVo<List<CategoryEntity>> queryLv2(@PathVariable("pid") Long pid){
        List<CategoryEntity> categoryEntities =  this.indexService.queryLvl2WithSubsByPid(pid);
        return ResponseVo.ok(categoryEntities);
    }


    @ResponseBody
    @GetMapping("index/test/lock")
    public ResponseVo testLock(){
        this.indexService.testLock();
        return ResponseVo.ok();
    }

    @ResponseBody
    @GetMapping("index/test/read")
    public ResponseVo<String> read(){
        String msg = indexService.readLock();

        return ResponseVo.ok(msg);
    }

    @ResponseBody
    @GetMapping("index/test/write")
    public ResponseVo<String> write(){
        String msg = indexService.writeLock();

        return ResponseVo.ok(msg);
    }

    @ResponseBody
    @GetMapping("index/test/latch")
    public ResponseVo<String> latch(){
        String msg = indexService.latch();

        return ResponseVo.ok(msg);
    }

    @ResponseBody
    @GetMapping("index/test/countDown")
    public ResponseVo<String> countDown(){
        String msg = indexService.countDown();

        return ResponseVo.ok(msg);
    }
}
