package com.atguigu.gmall.pms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.pms.entity.CategoryEntity;

import java.util.List;
import java.util.Map;

/**
 * 商品三级分类
 *
 * @author linyuan
 * @email 1471166577@qq.com
 * @date 2021-06-22 17:07:34
 */
public interface CategoryService extends IService<CategoryEntity> {

    PageResultVo queryPage(PageParamVo paramVo);

    List<CategoryEntity> queryCategoriesByPid(long parentId);

    List<CategoryEntity> queryLvl2WithSubsByPid(Long pid);

    List<CategoryEntity> queryLvl123CategoriesByCid3(Long cid);
}

