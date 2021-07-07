package com.atguigu.gmall.search.listener;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.api.GmallPmsApi;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchAttrValue;
import com.atguigu.gmall.search.repository.GoodsRespository;
import com.atguigu.gmall.wms.api.GmallWmsApi;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.repository.support.AbstractElasticsearchRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ItemListener {


    @Autowired
    private GmallPmsApi pmsClient;
    @Autowired
    private GmallWmsApi wmsClient;
    @Autowired
    private GoodsRespository goodsRespository;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue("SEARCH_ITEM_QUEUE"),
            exchange = @Exchange(value = "PMS_ITEM_EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"item.insert"}
    ))
    public void listener(Long spuId, Channel channel, Message message) throws Exception {

        if (spuId == null) {
             channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }

        ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(spuId);
        SpuEntity spuEntity = spuEntityResponseVo.getData();

        ResponseVo<List<SkuEntity>> skuResponseVo = this.pmsClient.querySkuBySpuId(spuId);
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
                goods.setTitle(skuEntity.getTitle());
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
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }
}

