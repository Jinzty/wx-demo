package io.jinzty.wxdemo.interceptor;

import io.jinzty.wxdemo.util.WxUtils;
import io.jinzty.wxdemo.websocket.WxWebSocket;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class WxInterceptor extends HandlerInterceptorAdapter {
    private static Logger logger = LoggerFactory.getLogger(WxInterceptor.class);
    @Autowired
    private RestTemplate restTemplate;
    @Value("${wxMp.appid}")
    private String appid;
    @Value("${wxMp.secret}")
    private String secret;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String uuid = uri.substring(uri.lastIndexOf("/") + 1);
        String state = request.getParameter("state");
        WxWebSocket.checkState(uuid, state);
        String code = request.getParameter("code");
        HttpSession session = request.getSession();
        String accessToken = (String) session.getAttribute("accessToken");
        if (StringUtils.isBlank(accessToken)) {
            String accessTokenUrl = WxUtils.getAccessTokenUrl(appid, secret, code);
            accessToken = "test";//restTemplate.getForObject(accessTokenUrl, String.class);
            WxWebSocket.sendMessage(uuid, accessToken);
            if (StringUtils.isBlank(accessToken)) {
                logger.error("---------获取accessToken失败--------");
                return false;
            }
            session.setAttribute("accessToken", accessToken);
        }
        return true;
    }
}
