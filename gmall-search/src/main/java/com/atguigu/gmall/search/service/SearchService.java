package com.atguigu.gmall.search.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchParamVo;
import com.atguigu.gmall.search.pojo.SearchResponseAttrVo;
import com.atguigu.gmall.search.pojo.SearchResponseVo;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    public SearchResponseVo search(SearchParamVo paramVo) {

        try {
            SearchResponse response = this.restHighLevelClient.search(new SearchRequest(new String[]{"goods"}, builderDsl(paramVo)), RequestOptions.DEFAULT);
            System.out.println(response);

            SearchResponseVo responseVo = this.parseResult(response);
            responseVo.setPageNum(paramVo.getPageNum());
            responseVo.setPageSize(paramVo.getPageSize());
            System.out.println(responseVo);
            return responseVo;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private SearchResponseVo parseResult(SearchResponse response){
        SearchResponseVo responseVo = new SearchResponseVo();

        SearchHits hits = response.getHits();
        responseVo.setTotal(hits.getTotalHits());
        SearchHit[] hitsHits = hits.getHits();
        List<Goods> goodsList = Arrays.stream(hitsHits).map(hitsHit -> {
            // ????????????hits???_source ??????
            String goodsJson = hitsHit.getSourceAsString();
            // ???????????????goods??????
            Goods goods = JSON.parseObject(goodsJson, Goods.class);

            // ???????????????title???????????????title
            Map<String, HighlightField> highlightFields = hitsHit.getHighlightFields();
            HighlightField highlightField = highlightFields.get("title");
            String highlightTitle = highlightField.getFragments()[0].toString();
            goods.setTitle(highlightTitle);
            return goods;
        }).collect(Collectors.toList());
        responseVo.setGoodsList(goodsList);

        Aggregations aggregations = response.getAggregations();
        ParsedLongTerms brandIdAgg = (ParsedLongTerms)aggregations.get("brandIdAgg");
        List<? extends Terms.Bucket> buckets = brandIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(buckets)){
            List<BrandEntity> brands = buckets.stream().map(bucket -> {
                BrandEntity brandEntity = new BrandEntity();
                brandEntity.setId(((Terms.Bucket)bucket).getKeyAsNumber().longValue());
                Map<String, Aggregation> brandAggregationMap = ((Terms.Bucket) bucket).getAggregations().asMap();
                ParsedStringTerms brandNameAgg = (ParsedStringTerms) brandAggregationMap.get("brandNameAgg");
                brandEntity.setName(brandNameAgg.getBuckets().get(0).getKeyAsString());
                ParsedStringTerms logoAgg = (ParsedStringTerms) brandAggregationMap.get("logoAgg");
                List<? extends Terms.Bucket> logoAggBuckets = logoAgg.getBuckets();
                if (!CollectionUtils.isEmpty(logoAggBuckets)){
                    brandEntity.setLogo(logoAggBuckets.get(0).getKeyAsString());
                }

                return brandEntity;
            }).collect(Collectors.toList());
            responseVo.setBrands(brands);
        }

        ParsedLongTerms categoryIdAgg = (ParsedLongTerms) aggregations.get("categoryIdAgg");
        List<? extends Terms.Bucket> categoryIdAggBuckets = categoryIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(categoryIdAggBuckets)){
            List<CategoryEntity> categoryEntities = categoryIdAggBuckets.stream().map(bucket -> {
                CategoryEntity categoryEntity = new CategoryEntity();
                categoryEntity.setId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                ParsedStringTerms categoryNameAgg = (ParsedStringTerms) ((Terms.Bucket) bucket).getAggregations().get("categoryNameAgg");
                categoryEntity.setName(categoryNameAgg.getBuckets().get(0).getKeyAsString());

                return categoryEntity;
            }).collect(Collectors.toList());
            responseVo.setCategorys(categoryEntities);
        }

        ParsedNested attrAgg = (ParsedNested) aggregations.get("attrAgg");
        ParsedLongTerms attrIdAgg = (ParsedLongTerms) attrAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> attrIdAggBuckets = attrIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(attrIdAggBuckets)){
            List<SearchResponseAttrVo> filters = attrIdAggBuckets.stream().map(bucket -> {
                SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
                searchResponseAttrVo.setAttrId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());

                ParsedStringTerms attrNameAgg = (ParsedStringTerms) bucket.getAggregations().get("attrNameAgg");
                List<? extends Terms.Bucket> attrNameAggBuckets = attrNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(attrNameAggBuckets)){
                    searchResponseAttrVo.setAttrName(attrNameAggBuckets.get(0).getKeyAsString());
                }

                ParsedStringTerms attrValueAgg = (ParsedStringTerms) bucket.getAggregations().get("attrValueAgg");
                List<? extends Terms.Bucket> attrValueAggBuckets = attrValueAgg.getBuckets();
                if (!CollectionUtils.isEmpty(attrValueAggBuckets)){
                    List<String> attrValues = attrValueAggBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                    searchResponseAttrVo.setAttrValues(attrValues);
                }
                return searchResponseAttrVo;
            }).collect(Collectors.toList());
            responseVo.setFilters(filters);
        }

        return responseVo;
    }

    private SearchSourceBuilder builderDsl(SearchParamVo paramVo){
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        String keyword = paramVo.getKeyword();
        if (StringUtils.isBlank(keyword)){

            return  sourceBuilder;
        }

        //1 ??????????????????
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);
        boolQueryBuilder.must(QueryBuilders.matchQuery("title",keyword).operator(Operator.AND));

        List<Long> brandId = paramVo.getBrandId();
        if (!CollectionUtils.isEmpty(brandId)){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId",brandId));
        }
        List<Long> categoryId = paramVo.getCategoryId();
        if (!CollectionUtils.isEmpty(categoryId)){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("categoryId",categoryId));
        }
        Double priceFrom = paramVo.getPriceFrom();
        Double priceTo = paramVo.getPriceTo();
        if (priceFrom != null || priceTo !=null){
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("price");
            boolQueryBuilder.filter(rangeQuery);
            if (priceFrom!=null){
                rangeQuery.gte(priceFrom);
            }
            if (priceTo!=null){
                rangeQuery.lte(priceTo);
            }
        }

        Boolean store = paramVo.getStore();
        if (store!=null){
            boolQueryBuilder.filter(QueryBuilders.termQuery("store",store));
        }

        List<String> props = paramVo.getProps();
        if (!CollectionUtils.isEmpty(props)){
            props.forEach(prop -> {  // 4:8G-12G
                // ????????????????????????????????????????????????????????????attrId ??? attrValue
                String[] attr = StringUtils.split(prop, ":");
                if (attr != null && attr.length == 2){ // ?????????????????????????????????2??????????????????
                    // ?????????????????????????????????????????????????????????????????????
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    boolQueryBuilder.filter(QueryBuilders.nestedQuery("searchAttrs", boolQuery, ScoreMode.None));

                    // bool??????????????????????????????????????????must??????
                    boolQuery.must(QueryBuilders.termQuery("searchAttrs.attrId", attr[0]));
                    boolQuery.must(QueryBuilders.termsQuery("searchAttrs.attrValue", StringUtils.split(attr[1], "-")));
                }
            });
        }

        //2 ????????????
        Integer sort = paramVo.getSort();
        switch (sort){
            case 0: sourceBuilder.sort("_score", SortOrder.DESC);break;
            case 1: sourceBuilder.sort("price", SortOrder.DESC);break;
            case 2: sourceBuilder.sort("price", SortOrder.ASC);break;
            case 3: sourceBuilder.sort("sales", SortOrder.DESC);break;
            case 4: sourceBuilder.sort("createTime", SortOrder.DESC);break;
            default:
                throw new RuntimeException("???????????????????????????");
        }

        //??????
        Integer pageNum = paramVo.getPageNum();
        Integer pageSize = paramVo.getPageSize();
        sourceBuilder.from((pageNum-1)*pageSize);
        sourceBuilder.size(pageSize);

        //??????
        sourceBuilder.highlighter(new HighlightBuilder().field("title").preTags("<font style='color:red;'>").postTags("</font>"));

        //??????
        sourceBuilder.aggregation(
                AggregationBuilders.terms("brandIdAgg").field("brandId")
                        .subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName"))
                        .subAggregation(AggregationBuilders.terms("logoAgg").field("logo"))
        );
        sourceBuilder.aggregation(
                AggregationBuilders.terms("categoryIdAgg").field("categoryId")
                        .subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName"))
        );
        sourceBuilder.aggregation(
                AggregationBuilders.nested("attrAgg","searchAttrs")
                        .subAggregation(AggregationBuilders.terms("attrIdAgg").field("searchAttrs.attrId")
                                .subAggregation(AggregationBuilders.terms("attrNameAgg").field("searchAttrs.attrName"))
                                .subAggregation(AggregationBuilders.terms("attrValueAgg").field("searchAttrs.attrValue"))
                         )
        );

        //sourceBuilder.fetchSource(new String[]{"skuId","title","subTitle","price","degaultImage"},null);


        System.out.println(sourceBuilder);
        return sourceBuilder;
    }
}
