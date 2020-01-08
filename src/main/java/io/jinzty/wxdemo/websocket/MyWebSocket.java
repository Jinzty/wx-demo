package io.jinzty.wxdemo.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

@ServerEndpoint(value = "/websocket")
@Component
public class MyWebSocket {
    private static Logger logger = LoggerFactory.getLogger(MyWebSocket.class);
    //静态变量，用来记录当前在线连接数。
    private static int onlineCount = 0;
    //concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。
    private static CopyOnWriteArraySet<MyWebSocket> webSocketSet = new CopyOnWriteArraySet<>();
    //与某个客户端的连接会话，需要通过它来给客户端发送数据
    private Session session;

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        webSocketSet.add(this);
        addOnlineCount();           //在线数加1
        logger.info("open sessionId:{}, now count:{}", session.getId(), getOnlineCount());
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(Session session) {
        webSocketSet.remove(this);
        subOnlineCount();           //在线数减1
        logger.info("close sessionId:{}, now count:{}", session.getId(), getOnlineCount());
        try {
            session.close();
        } catch (IOException e) {
            logger.error("close error！", e);
        }
    }

    /**
     * 收到客户端消息后调用的方法
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        logger.info("sessionId:{},message:{}", session.getId(), message);

        MyWebSocket.allSendMessage(message, session.getId());
    }

    /**
     * 发生错误时调用
     */
    @OnError
    public void onError(Session session, Throwable error) {
        logger.error("sessionId:" + session.getId(), error);
    }

    /**
     * 发消息
     *
     * @param message
     * @param sessionId
     * @throws IOException
     */
    private void sendMessage(String message, String sessionId) throws IOException {
        if (Objects.equals(this.session.getId(), sessionId)) {
            this.session.getBasicRemote().sendText(String.format("me(%s) say: %s", session.getId(), message));
        } else {
            this.session.getAsyncRemote().sendText(String.format("%s say: %s", session.getId(), message));
        }
    }

    /**
     * 群发消息
     *
     * @param message
     * @param sessionId
     */
    public static void allSendMessage(String message, String sessionId) {
        for (MyWebSocket item : webSocketSet) {
            try {
                item.sendMessage(message, sessionId);
            } catch (IOException e) {
                logger.error(item.session.getId(), e);
                continue;
            }
        }
    }

    public static synchronized int getOnlineCount() {
        return onlineCount;
    }

    public static synchronized void addOnlineCount() {
        MyWebSocket.onlineCount++;
    }

    public static synchronized void subOnlineCount() {
        MyWebSocket.onlineCount--;
    }
}
