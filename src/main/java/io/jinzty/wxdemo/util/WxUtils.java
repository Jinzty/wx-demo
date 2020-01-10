package io.jinzty.wxdemo.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class WxUtils {

    /**
     * 构建wx公众号授权地址，用户同意授权，获取code
     *
     * @param appid
     * @param redirectUri
     * @param userInfoScope
     * @param state
     * @return
     * @throws UnsupportedEncodingException
     */
    public static String getMpAuthUrl(String appid, String redirectUri, boolean userInfoScope, String state) throws UnsupportedEncodingException {
        String scope = userInfoScope ? "snsapi_userinfo" : "snsapi_base";
        StringBuilder sb = new StringBuilder("https://open.weixin.qq.com/connect/oauth2/authorize");
        sb.append("?appid=").append(appid);
        sb.append("&redirect_uri=").append(URLEncoder.encode(redirectUri, "utf-8"));
        sb.append("&response_type=code&scope=").append(scope);
        sb.append("&state=").append(state).append("#wechat_redirect");
        return sb.toString();
    }

    /**
     * 通过code换取网页授权access_token地址
     *
     * @param appid
     * @param secret
     * @param code
     * @return
     */
    public static String getAccessTokenUrl(String appid, String secret, String code) {
        StringBuilder sb = new StringBuilder("https://api.weixin.qq.com/sns/oauth2/access_token");
        sb.append("?appid=").append(appid);
        sb.append("&secret=").append(secret);
        sb.append("&code=").append(code);
        sb.append("&grant_type=authorization_code");
        return sb.toString();
    }
}
