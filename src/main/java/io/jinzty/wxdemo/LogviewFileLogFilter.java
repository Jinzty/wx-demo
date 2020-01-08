package io.jinzty.wxdemo;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * 业务日志过滤器
 */
public class LogviewFileLogFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event.getLevel() == Level.ERROR) {
            if (event.getLoggerName().startsWith("org.apache.catalina.core.ContainerBase")
                    && event.getMessage().contains("org.apache.shiro.session.UnknownSessionException")) {
                return FilterReply.DENY;
            }
        }
        return FilterReply.ACCEPT;
    }
}
