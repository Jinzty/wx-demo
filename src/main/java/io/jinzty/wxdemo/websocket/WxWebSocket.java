package io.jinzty.wxdemo.websocket;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.zxing.WriterException;
import io.jinzty.wxdemo.WebAppInitializer;
import io.jinzty.wxdemo.util.QRCodeUtils;
import io.jinzty.wxdemo.util.WxUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpSession;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@ServerEndpoint(value = "/wxWs", configurator = GetHttpSessionConfigurator.class)
@Component
public class WxWebSocket {
    private static Logger logger = LoggerFactory.getLogger(WxWebSocket.class);
    private static ConcurrentHashMap<String, Session> sessionMap = new ConcurrentHashMap<>();
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
                WxWebSocket.sendMessage(String.valueOf(notification.getKey()), "超时请重新刷新页面");
            }).build();

    //@Value("${appid}")
    private static String appid;
    private static String origin;

    static {
        ApplicationContext applicationContext = WebAppInitializer.getApplicationContext();//ContextLoader.getCurrentWebApplicationContext()
        appid = applicationContext.getEnvironment().getProperty("wxMp.appid");
        origin = applicationContext.getEnvironment().getProperty("origin");
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) throws IOException, WriterException {
        HttpSession httpSession = (HttpSession) config.getUserProperties().get(HttpSession.class.getName());
        logger.info("httpSessionId:{}", httpSession.getId());
        String uuid = DigestUtils.md5DigestAsHex((session.getId()).getBytes()).toLowerCase();
        logger.info("open sessionId:{}, uuid:{}", session.getId(), uuid);
        sessionMap.put(uuid, session);
        String redirectUri = String.format("%s/wxWs/callback/%s", origin, uuid);
        String sessionState = "init";
        String key = String.format("loginState_%s", uuid);
        loadingCache.put(key, sessionState);
        String state = DigestUtils.md5DigestAsHex((uuid + sessionState).getBytes()).toLowerCase();
        String authUrl = WxUtils.getMpAuthUrl(appid, redirectUri, true, state);
        byte[] qrCode = QRCodeUtils.encode(360, 360, authUrl);
        ByteBuffer byteBuffer = ByteBuffer.wrap(qrCode);
        session.getBasicRemote().sendBinary(byteBuffer);
    }

    @OnClose
    public void onClose(Session session) {
        String uuid = DigestUtils.md5DigestAsHex((session.getId()).getBytes()).toLowerCase();
        sessionMap.remove(uuid);
        String key = String.format("loginState_%s", uuid);
        loadingCache.invalidate(key);
        try {
            session.close();
        } catch (IOException e) {
            logger.error("close error！", e);
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        logger.info("sessionId:{},message:{}", session.getId(), message);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        logger.error("sessionId:" + session.getId(), error);
    }

    public static void checkState(String uuid, String state) {
        String key = String.format("loginState_%s", uuid);
        String sessionState = loadingCache.getIfPresent(key);
        Assert.notNull(sessionState, "失效请刷新二维码重试");
        String sign = DigestUtils.md5DigestAsHex((uuid + sessionState).getBytes()).toLowerCase();
        Assert.isTrue(Objects.equals(sign, state), "验签失败");
        loadingCache.put(key, "someone");
        sendMessage(uuid, "someone");
    }

    public static void sendMessage(String uuid, String message) {
        Session session = sessionMap.get(uuid);
        if (session != null && session.isOpen()) {
            session.getAsyncRemote().sendText(message);
        }
    }
}
