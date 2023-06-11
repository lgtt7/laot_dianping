package com.hmdp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.service.Parameter;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableSwagger2
public class SwaggerConfig {
    //配置了swagger的Docker的bean实例
    @Bean
    public Docket docket() {
        //添加head参数配置start
        ParameterBuilder tokenPar = new ParameterBuilder();
        List<Parameter> pars = new ArrayList<>();
        tokenPar.name("Authorization").description("令牌").modelRef(new ModelRef("string")).parameterType("header").required(false).build();
        pars.add(tokenPar.build());



        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .select()
                //包下的类，生成接口文档
                .apis(RequestHandlerSelectors.basePackage("com.hmdp.controller"))
                .build()
                .globalOperationParameters(pars);
    }

    //配置Swagger信息
    private ApiInfo apiInfo() {
//        作者信息
        Contact contact = new Contact("LaoT", "https://www.baidu.com", "1621499632@qq.com");
        return new ApiInfoBuilder()
                .contact(contact)
                .title("充电了")
                .description("打卡！！！")
                .termsOfServiceUrl("https://www.baidu.com")
                .version("3.0")
                .build();
    }
}
