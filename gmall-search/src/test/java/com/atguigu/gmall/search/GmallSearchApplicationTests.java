package com.atguigu.gmall.search;


import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.feigin.GmallPmsClient;
import com.atguigu.gmall.search.feigin.GmallWmsClient;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchAttrValue;
import com.atguigu.gmall.search.repository.GoodsRespository;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
class GmallSearchApplicationTests {

    @Autowired
    private ElasticsearchRestTemplate restTemplate;

    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GoodsRespository goodsRespository;

    @Test
    void contextLoads() {

        if (!this.restTemplate.indexExists(Goods.class)){
            this.restTemplate.createIndex(Goods.class);
            this.restTemplate.putMapping(Goods.class);
        }

        Integer pageNum = 1;
        Integer pageSize = 100;

        do{
            PageParamVo pageParamVo = new PageParamVo(pageNum, pageSize, null);
            ResponseVo<List<SpuEntity>> responseVo = this.pmsClient.querySpuByPageJson(pageParamVo);
            List<SpuEntity> spuEntities = responseVo.getData();
            if (CollectionUtils.isEmpty(spuEntities)){
                return;
            }

            spuEntities.forEach(spuEntity -> {
                ResponseVo<List<SkuEntity>> skuResponseVo = this.pmsClient.querySkuBySpuId(spuEntity.getId());
                List<SkuEntity> skuEntities = skuResponseVo.getData();
                if (!CollectionUtils.isEmpty(skuEntities)){

                    ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(spuEntity.getBrandId());
                    BrandEntity brandEntity = brandEntityResponseVo.getData();
                    ResponseVo<CategoryEntity> categoryEntityResponseVo = this.pmsClient.queryCategoryById(spuEntity.getCategoryId());
                    CategoryEntity categoryEntity = categoryEntityResponseVo.getData();

                    List<Goods> goodsList = skuEntities.stream().map(skuEntity -> {
                        Goods goods = new Goods();

                        goods.setSkuId(skuEntity.getId());
                        goods.setDefaultImage(skuEntity.getDefaultImage());
                        goods.setSubTitle(skuEntity.getTitle());
                        goods.setSubTitle(skuEntity.getSubtitle());
                        goods.setPrice(skuEntity.getPrice().doubleValue());

                        goods.setCreateTime(spuEntity.getCreateTime());

                        ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkuBySkuId(skuEntity.getId());
                        List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
                        if (!CollectionUtils.isEmpty(wareSkuEntities)){
                            goods.setSales(wareSkuEntities.stream().mapToLong(WareSkuEntity::getSales).reduce((a,b) -> a + b).getAsLong());
                            goods.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
                        }

                        if (brandEntity!=null){
                            goods.setBrandId(brandEntity.getId());
                            goods.setBrandName(brandEntity.getName());
                            goods.setLogo(brandEntity.getLogo());
                        }
                        if (categoryEntity!=null){
                            goods.setCategoryId(categoryEntity.getId());
                            goods.setCategoryName(categoryEntity.getName());
                        }

                        ArrayList<SearchAttrValue> attrValueVos = new ArrayList<>();
                        ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = this.pmsClient.querySearchAttrValueBySkuId(skuEntity.getCategoryId(), skuEntity.getId());
                        List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo.getData();
                        if (!CollectionUtils.isEmpty(skuAttrValueEntities)){
                            attrValueVos.addAll(
                                    skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
                                        SearchAttrValue attrValue = new SearchAttrValue();
                                        BeanUtils.copyProperties(skuAttrValueEntity,attrValue);
                                        return attrValue;
                                    }).collect(Collectors.toList())
                            );
                        }
                        ResponseVo<List<SpuAttrValueEntity>> baseAttrResponseVo = this.pmsClient.querySearchAttrValueBySpuId(skuEntity.getCategoryId(), spuEntity.getId());
                        List<SpuAttrValueEntity> spuAttrValueEntities = baseAttrResponseVo.getData();
                        if (!CollectionUtils.isEmpty(spuAttrValueEntities)){
                            attrValueVos.addAll(
                                    spuAttrValueEntities.stream().map(spuAttrValueEntity -> {
                                        SearchAttrValue attrValue = new SearchAttrValue();
                                        BeanUtils.copyProperties(spuAttrValueEntity,attrValue);
                                        return attrValue;
                                    }).collect(Collectors.toList())
                            );
                        }
                        goods.setSearchAttrs(attrValueVos);

                        return goods;
                    }).collect(Collectors.toList());
                    this.goodsRespository.saveAll(goodsList);
                }
            });


            pageSize = spuEntities.size();
            pageNum++;
        }while (pageSize==100);

    }

}
