package io.jinzty.wxdemo.controller;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/wx")
@Api("wx接口")
public class WxController {
    private static Logger logger = LoggerFactory.getLogger(WxController.class);
    private static Cache<String, String> loadingCache = CacheBuilder.newBuilder()
            /*设置缓存容器的初始容量大小为10*/
            .initialCapacity(100)
            /*设置缓存容器的最大容量大小为100*/
            .maximumSize(200)
            /*设置记录缓存命中率*/
            .recordStats()
            /*设置并发级别为8，智并发基本值可以同事些缓存的线程数*/
            .concurrencyLevel(20)
            /*设置过期时间为5分钟*/
            .expireAfterWrite(5, TimeUnit.MINUTES)
            //设置缓存的移除通知
            .removalListener(notification -> {
                logger.info("", "", notification.getKey() + " " + notification.getValue() + " 被移除,原因:" + notification.getCause());
            }).build();

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    @ApiOperation("登录页跳转")
    public void login(HttpSession session, HttpServletResponse response) throws IOException {
        String appid = "wx9e83c88e7e5200dc";
        String uuid = RandomStringUtils.randomAlphanumeric(24);
        String redirectUri = "https://72933770.ngrok.io/test/wx/home";
        String state = DigestUtils.md5DigestAsHex((uuid + session.getId()).getBytes()).toLowerCase();
        session.setAttribute("uuid", uuid);
        logger.info("login sessionId:{} uuid:{}", session.getId(), uuid);
        StringBuilder sb = new StringBuilder("https://open.weixin.qq.com/connect/qrconnect");
        sb.append("?appid=").append(appid);
        sb.append("&redirect_uri=").append(URLEncoder.encode(redirectUri, "utf-8"));
        sb.append("&response_type=code&scope=").append("snsapi_login");
        sb.append("&state=").append(state).append("#wechat_redirect");
        response.sendRedirect(sb.toString());
    }

    @GetMapping("/login/home")
    @ApiOperation("登录页回调")
    public String loginHome(String state, String code, HttpSession session) {
        String uuid = String.valueOf(session.getAttribute("uuid"));
        Assert.notNull(uuid, "失效请重新登录");
        String sign = DigestUtils.md5DigestAsHex((uuid + session.getId()).getBytes()).toLowerCase();
        Assert.isTrue(Objects.equals(sign, state), "验签失败");
        String OPENID = code;//消费code获取OPENID
        String userId = OPENID;//根据OPENID获取关联userId
        logger.info("loginHome sessionId:{} uuid:{}", session.getId(), uuid, userId);
        //登录处理
        return "login home";
    }


    @GetMapping("/login/qrCode")
    @ApiOperation("登录授权二维码")
    public ResponseEntity<byte[]> loginQrCode(@RequestParam(defaultValue = "360") Integer width, @RequestParam(defaultValue = "360") Integer height, HttpSession session)
            throws WriterException, IOException {
        String appid = "wx9e83c88e7e5200dc";
        String scope = "snsapi_userinfo";//"snsapi_base";
        String uuid = RandomStringUtils.randomAlphanumeric(24);
        String redirectUri = "https://72933770.ngrok.io/test/wx/login/callback/" + uuid;
        String sessionState = "init";
        String state = DigestUtils.md5DigestAsHex((uuid + sessionState).getBytes()).toLowerCase();
        String key = String.format("loginState_%s", uuid);
        loadingCache.put(key, sessionState);
        session.setAttribute("key", key);
        logger.info("login qrCode sessionId:{} key:{}", session.getId(), key);
        StringBuilder sb = new StringBuilder("https://open.weixin.qq.com/connect/oauth2/authorize");
        sb.append("?appid=").append(appid);
        sb.append("&redirect_uri=").append(URLEncoder.encode(redirectUri, "utf-8"));
        sb.append("&response_type=code&scope=").append(scope);
        sb.append("&state=").append(state).append("#wechat_redirect");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        return new ResponseEntity<>(getQRCodeImage(width, height, sb.toString()), headers, HttpStatus.CREATED);
    }

    @GetMapping("/login/check")
    @ApiOperation("登录轮询")
    public String loginCheck(HttpSession session) throws IOException {
        String key = String.valueOf(session.getAttribute("key"));
        if (key == null) {
            return "don't check";
        }
        String sessionState = loadingCache.getIfPresent(key);
        Assert.notNull(sessionState, "失效请重新登录");
        if (!"commit".equals(sessionState)) {
            return sessionState;
        }
        loadingCache.invalidate(key);
        String commitKey = String.format("commit_%s", key);
        String userId = loadingCache.getIfPresent(commitKey);
        loadingCache.invalidate(commitKey);
        logger.info("login check sessionId:{} key:{} userId:{}", session.getId(), key, userId);
        //登录处理
        return "login";
    }

    @GetMapping("/login/callback/{uuid}")
    @ApiOperation("wx登录回调")
    public String loginCallback(@PathVariable(value = "uuid") String uuid, String state, String code){
        String key = String.format("loginState_%s", uuid);
        String sessionState = loadingCache.getIfPresent(key);
        Assert.notNull(sessionState, "失效请刷新二维码重试");
        String sign = DigestUtils.md5DigestAsHex((uuid + sessionState).getBytes()).toLowerCase();
        Assert.isTrue(Objects.equals(sign, state), "验签失败");
        loadingCache.put(key, "someone");
        String OPENID = code;//消费code获取OPENID
        String userId = OPENID;//根据OPENID获取关联userId
        if (userId == null) {
            loadingCache.invalidate(key);
            return "no binding";
        }
        loadingCache.put(key, "waiting");
//        logger.info("login callback sessionId:{} key:{} userId:{}", session.getId(), key, userId);
//        session.setAttribute("key", key);
//        session.setAttribute("userId", userId);
//        return "hi, man";
////        response.sendRedirect("/login/commit");
//    }
//    @GetMapping("/login/commit")
//    @ApiOperation("wx登录提交")
//    public String loginCommit(HttpSession session) {
//        String key = String.valueOf(session.getAttribute("key"));
//        String userId = String.valueOf(session.getAttribute("userId"));
//        logger.info("login commit sessionId:{} key:{} userId:{}", session.getId(), key, userId);
        loadingCache.put(key, "commit");
        loadingCache.put(String.format("commit_%s", key), userId);
        return "login callback";
    }


    @GetMapping("/auth/qrCode")
    @ApiOperation("user授权二维码")
    public ResponseEntity<byte[]> authQrCode(@RequestParam(defaultValue = "360") Integer width, @RequestParam(defaultValue = "360") Integer height)
            throws WriterException, IOException {
        String appid = "wx9e83c88e7e5200dc";
        String scope = "snsapi_userinfo";
        String uuid = RandomStringUtils.randomAlphanumeric(24);
        String redirectUri = "https://72933770.ngrok.io/test/wx/auth/callback/" + uuid;
        String userId = "test";
        String state = DigestUtils.md5DigestAsHex((uuid + userId).getBytes()).toLowerCase();
        loadingCache.put(String.format("auth_%s", uuid), userId);
        StringBuilder sb = new StringBuilder("https://open.weixin.qq.com/connect/oauth2/authorize");
        sb.append("?appid=").append(appid);
        sb.append("&redirect_uri=").append(URLEncoder.encode(redirectUri, "utf-8"));
        sb.append("&response_type=code&scope=").append(scope);
        sb.append("&state=").append(state).append("#wechat_redirect");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        return new ResponseEntity<>(getQRCodeImage(width, height, sb.toString()), headers, HttpStatus.CREATED);
    }

    private byte[] getQRCodeImage(int width, int height, String url) throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, width, height);
        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
        byte[] pngData = pngOutputStream.toByteArray();
        return pngData;
    }

    @GetMapping("/auth/callback/{uuid}")
    @ApiOperation("wx授权回调")
    public String authCallback(@PathVariable(value = "uuid") String uuid, String state, String code,
                               HttpSession session, HttpServletResponse response) throws IOException {
        String key = String.format("auth_%s", uuid);
        String userId = loadingCache.getIfPresent(key);
        Assert.notNull(userId, "失效请刷新二维码重试");
        String sign = DigestUtils.md5DigestAsHex((uuid + userId).getBytes()).toLowerCase();
        Assert.isTrue(Objects.equals(sign, state), "验签失败");
        loadingCache.invalidate(key);
        String OPENID = code;//消费code获取OPENID
        logger.info("auth callback sessionId:{} userId:{} OPENID:{}", session.getId(), userId, OPENID);
        session.setAttribute("userId", userId);
        session.setAttribute("OPENID", OPENID);
        return "hi, man";
//        response.sendRedirect("/auth/binding");
    }

    @GetMapping("/auth/binding")
    @ApiOperation("wx绑定")
    public String authBinding(HttpSession session) {
        logger.info("auth binding sessionId:{} userId:{} OPENID:{}", session.getId(), session.getAttribute("userId"), session.getAttribute("OPENID"));
        return "binding";
    }
}
