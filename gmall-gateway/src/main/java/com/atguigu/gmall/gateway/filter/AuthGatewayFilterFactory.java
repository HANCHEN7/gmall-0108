package com.atguigu.gmall.gateway.filter;

import com.atguigu.gmall.common.utils.IpUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.gateway.config.JwtProperties;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@EnableConfigurationProperties(JwtProperties.class)
@Component
public class AuthGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthGatewayFilterFactory.PathConfig> {


    @Autowired
    private JwtProperties properties;

    public AuthGatewayFilterFactory(){
        super(PathConfig.class);
    }

    @Override
    public List<String> shortcutFieldOrder(){
        return Arrays.asList("paths");
    }

    @Override
    public ShortcutType shortcutType() {
        return ShortcutType.GATHER_LIST;
    }

    @Override
    public GatewayFilter apply(PathConfig config) {
        return new GatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                //System.out.println("我是局部过滤器，只拦截指定路由的请求  paths=" + config.paths);

                ServerHttpRequest request = exchange.getRequest();
                ServerHttpResponse response = exchange.getResponse();

                String curPath = request.getURI().getPath();

                List<String> paths = config.paths;

                if (paths.stream().allMatch(path -> !curPath.startsWith(path))) {
                        return chain.filter(exchange);
                }

                String token = request.getHeaders().getFirst(properties.getToken());
                if (StringUtils.isBlank(token)) {
                    MultiValueMap<String, HttpCookie> cookies = request.getCookies();
                    HttpCookie cookie = cookies.getFirst(properties.getCookieName());
                    token = cookie.getValue();
                }

                if (StringUtils.isBlank(token)){
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    response.getHeaders().set(HttpHeaders.LOCATION,"http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                    return response.setComplete();
                }

                try {
                    Map<String, Object> map = JwtUtils.getInfoFromToken(token, properties.getPublicKey());

                    String ip = map.get("ip").toString();
                    String curIp = IpUtils.getIpAddressAtGateway(request);
                    if (!StringUtils.equals(ip,curIp)) {
                        response.setStatusCode(HttpStatus.SEE_OTHER);
                        response.getHeaders().set(HttpHeaders.LOCATION,"http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                        return response.setComplete();
                    }

                    request.mutate()
                            .header("userId",map.get("userId").toString())
                            .header("username",map.get("username").toString())
                            .build();
                    exchange.mutate().request(request).build();

                    return chain.filter(exchange);

                } catch (Exception e) {
                    e.printStackTrace();
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    response.getHeaders().set(HttpHeaders.LOCATION,"http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                    return response.setComplete();
                }
            }
        };
    }

    @Data
    public static class PathConfig{
//        public String key;
//        public String value;
        private List<String> paths;
    }
}
