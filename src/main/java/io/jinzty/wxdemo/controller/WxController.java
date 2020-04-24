package io.jinzty.wxdemo.controller;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.zxing.WriterException;
import io.jinzty.wxdemo.util.QRCodeUtils;
import io.jinzty.wxdemo.util.WxUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * wx接口
 */
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
                logger.info("缓存key:{},value:{}被移除,原因:{}", notification.getKey(), notification.getValue(), notification.getCause());
            }).build();

    @Value("${wxMp.appid}")
    private String appid;
    @Value("${origin}")
    private String origin;

    /**
     * 登录授权二维码
     *
     * @param width
     * @param height
     * @param session
     * @return
     * @throws WriterException
     * @throws IOException
     */
    @GetMapping("/login/qrCode")
    @ApiOperation("登录授权二维码")
    public ResponseEntity<byte[]> loginQrCode(@RequestParam(defaultValue = "360") Integer width, @RequestParam(defaultValue = "360") Integer height,
                                              HttpSession session) throws WriterException, IOException {
        String uuid = RandomStringUtils.randomAlphanumeric(24);
        String redirectUri = String.format("%s/wx/login/callback/%s", origin, uuid);
        String sessionState = "init";
        String key = String.format("loginState_%s", uuid);
        loadingCache.put(key, sessionState);
        session.setAttribute("key", key);
        logger.info("login qrCode sessionId:{} key:{}", session.getId(), key);
        String state = DigestUtils.md5DigestAsHex((uuid + sessionState).getBytes()).toLowerCase();
        String authUrl = WxUtils.getMpAuthUrl(appid, redirectUri, true, state);
        byte[] qrCode = QRCodeUtils.encode(360, 360, authUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.set("uuid", uuid);
        return new ResponseEntity<>(qrCode, headers, HttpStatus.CREATED);
    }

    /**
     * 登录轮询
     *
     * @param session
     * @return
     * @throws IOException
     */
    @GetMapping("/login/check")
    @ApiOperation("登录轮询")
    public String loginCheck(HttpSession session) {
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

    /**
     * wx登录回调
     *
     * @param uuid
     * @param state
     * @param code
     * @return
     */
    @GetMapping("/login/callback/{uuid}")
    @ApiOperation("wx登录回调")
    public String loginCallback(@PathVariable(value = "uuid") String uuid, String state, String code) {
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
        loadingCache.put(key, "commit");
        loadingCache.put(String.format("commit_%s", key), userId);
        return "login callback";
    }


    /**
     * user授权二维码
     *
     * @param width
     * @param height
     * @return
     * @throws WriterException
     * @throws IOException
     */
    @GetMapping("/auth/qrCode")
    @ApiOperation("user授权二维码")
    public ResponseEntity<byte[]> authQrCode(@RequestParam(defaultValue = "360") Integer width, @RequestParam(defaultValue = "360") Integer height)
            throws WriterException, IOException {
        String uuid = RandomStringUtils.randomAlphanumeric(24);
        String redirectUri = String.format("%s/wx/auth/callback/%s", origin, uuid);
        String userId = "test";
        loadingCache.put(String.format("auth_%s", uuid), userId);
        String state = DigestUtils.md5DigestAsHex((uuid + userId).getBytes()).toLowerCase();
        String authUrl = WxUtils.getMpAuthUrl(appid, redirectUri, false, state);
        byte[] qrCode = QRCodeUtils.encode(360, 360, authUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        return new ResponseEntity<>(qrCode, headers, HttpStatus.CREATED);
    }

    /**
     * wx授权回调
     *
     * @param uuid
     * @param state
     * @param code
     * @param session
     * @param response
     * @return
     * @throws IOException
     */
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

    /**
     * wx绑定
     *
     * @param session
     * @return
     */
    @GetMapping("/auth/binding")
    @ApiOperation("wx绑定")
    public String authBinding(HttpSession session) {
        logger.info("auth binding sessionId:{} userId:{} OPENID:{}", session.getId(), session.getAttribute("userId"), session.getAttribute("OPENID"));
        return "binding";
    }

}
