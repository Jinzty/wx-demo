package io.jinzty.wxdemo.controller

import io.jinzty.wxdemo.util.WxUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.RestTemplate
import javax.servlet.http.HttpSession

@Controller
@RequestMapping
class IndexController {
    private val logger = LoggerFactory.getLogger(IndexController::class.java)
    @Autowired
    private val restTemplate: RestTemplate? = null
    @Value("\${wxMp.appid}")
    private val appid: String? = null
    @Value("\${wxMp.secret}")
    private val secret: String? = null

    @RequestMapping
    fun index(): String {
        return "wxLogin"
    }

    @RequestMapping("/login/{code}")
    fun login(@PathVariable(value = "code") code: String, session: HttpSession): String {
        var accessToken = session.getAttribute("accessToken") as? String
        if (StringUtils.isBlank(accessToken)) {
            val accessTokenUrl = WxUtils.getAccessTokenUrl(appid, secret, code)
            accessToken = accessTokenUrl //restTemplate.getForObject(accessTokenUrl, String.class);
            if (StringUtils.isBlank(accessToken)) {
                logger.error("---------获取accessToken失败--------")
                return "wxLogin"
            }
            session.setAttribute("accessToken", accessToken)
        }
        return "index"
    }
}