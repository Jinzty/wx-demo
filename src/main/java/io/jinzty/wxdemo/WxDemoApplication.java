package io.jinzty.wxdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@PropertySource(ignoreResourceNotFound = true, value = {"${config-path}/wx-demo.properties"})
public class WxDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(WxDemoApplication.class, args);
	}

}
