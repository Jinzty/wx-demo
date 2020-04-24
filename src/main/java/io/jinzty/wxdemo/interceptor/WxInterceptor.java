package io.jinzty.wxdemo.interceptor;

import io.jinzty.wxdemo.websocket.WxWebSocket;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class WxInterceptor extends HandlerInterceptorAdapter {
    private static Logger logger = LoggerFactory.getLogger(WxInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String uuid = uri.substring(uri.lastIndexOf("/") + 1);
        String state = request.getParameter("state");
        WxWebSocket.checkState(uuid, state);
        String code = request.getParameter("code");
        if (StringUtils.isBlank(code)) {
            logger.error("---------获取code失败--------");
            return false;
        }
        WxWebSocket.sendMessage(uuid, code);
        return true;
    }
}
