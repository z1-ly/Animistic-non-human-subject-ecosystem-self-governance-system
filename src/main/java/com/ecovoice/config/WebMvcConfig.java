package com.ecovoice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 静态资源版本号，更新前端后递增以破除浏览器缓存 */
    public static final String STATIC_VERSION = "8";

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/entities").setViewName("forward:/entities.html");
        registry.addViewController("/vitals").setViewName("forward:/vitals.html");
        registry.addViewController("/appeals").setViewName("forward:/appeals.html");
        registry.addViewController("/ledger").setViewName("forward:/ledger.html");
        registry.addViewController("/tasks").setViewName("forward:/tasks.html");
        registry.addViewController("/ecosphere").setViewName("forward:/ecosphere.html");
        registry.addViewController("/redeem").setViewName("forward:/redeem.html");
        registry.addViewController("/network").setViewName("forward:/network.html");
        registry.addViewController("/stream").setViewName("forward:/stream.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        var noCache = org.springframework.http.CacheControl.noStore().mustRevalidate();
        registry.addResourceHandler("/*.html", "/js/**", "/css/**")
                .addResourceLocations(
                        "classpath:/static/",
                        "classpath:/static/js/",
                        "classpath:/static/css/")
                .setCacheControl(noCache);
    }
}
