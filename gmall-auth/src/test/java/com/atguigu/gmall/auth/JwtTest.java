package com.atguigu.gmall.auth;

import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.common.utils.RsaUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class JwtTest {

    // 别忘了创建D:\\project\rsa目录
	private static final String pubKeyPath = "D:\\1130java\\rsa\\rsa.pub";
    private static final String priKeyPath = "D:\\1130java\\rsa\\rsa.pri";

    private PublicKey publicKey;

    private PrivateKey privateKey;

    @Test
    public void testRsa() throws Exception {
        RsaUtils.generateKey(pubKeyPath, priKeyPath, "234");
    }

    @BeforeEach
    public void testGetRsa() throws Exception {
        this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
        this.privateKey = RsaUtils.getPrivateKey(priKeyPath);
    }

    @Test
    public void testGenerateToken() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "11");
        map.put("username", "liuyan");
        // 生成token
        String token = JwtUtils.generateToken(map, privateKey, 2);
        System.out.println("token = " + token);
    }

    @Test
    public void testParseToken() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJpZCI6IjExIiwidXNlcm5hbWUiOiJsaXV5YW4iLCJleHAiOjE2MjYzMzI0NTJ9.VbNPXw_-VBeLNboAPQ7yixVaj4f79SGHUHxQ1YbgkIL-k1x8JZlvpxf8t3gOo2Myh10exeD_PC97cwEGhtDiKwiaZp7doZOIlFWdqORpoVAh008kFQ51u0WLZaxPFk54cXBkSosm1zW3qF95ZYQmA_pWeNKZl_YV9llc48-NHVf3sFwKW0InyRFsdIRTCzHuh4--RXAjtreyu_4jg87cO5bM5-jeovkv2h1O1UECWZfQbIHsgt7OQmkYikxlOjyhZoADHOdafcBF7vjKrK_9SqxRFcVq7RBHcphb_eMBl9lgylDiH3eEzIEjuSFW_Gapm8jRGYgH-a2Y3V-IxVrWzA";

        // 解析token
        Map<String, Object> map = JwtUtils.getInfoFromToken(token, publicKey);
        System.out.println("id: " + map.get("id"));
        System.out.println("userName: " + map.get("username"));
    }
}