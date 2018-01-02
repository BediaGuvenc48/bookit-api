/* Licensed under Apache-2.0 */
package com.buildit.bookit

import com.buildit.bookit.auth.JwtAuthenticationFilter
import com.buildit.bookit.auth.OpenIdAuthenticator
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.autoconfigure.security.SecurityProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.Order
import org.springframework.data.jpa.convert.threeten.Jsr310JpaConverters
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy.STATELESS
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter
import springfox.bean.validators.configuration.BeanValidatorPluginsConfiguration
import springfox.documentation.builders.ApiInfoBuilder
import springfox.documentation.builders.OAuthBuilder
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.AuthorizationScope
import springfox.documentation.service.BasicAuth
import springfox.documentation.service.ImplicitGrant
import springfox.documentation.service.LoginEndpoint
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger.web.ApiKeyVehicle
import springfox.documentation.swagger.web.SecurityConfiguration
import springfox.documentation.swagger2.annotations.EnableSwagger2
import java.time.Clock
import java.time.ZoneId

/**
 * Main class (needed for spring boot integration)
 */
@SpringBootApplication
@EnableConfigurationProperties(BookitProperties::class)
@EntityScan(
    basePackageClasses = [BookitApplication::class, Jsr310JpaConverters.ZoneIdConverter::class]
)
class BookitApplication {
    @Bean
    fun defaultClock(): Clock = Clock.systemUTC()
}

/**
 * Swagger configuration
 */
@Configuration
@EnableSwagger2
@Import(BeanValidatorPluginsConfiguration::class)
class SwaggerConfiguration {
    /**
     * Swagger configuration
     */
    @Bean
    fun api(): Docket = Docket(DocumentationType.SWAGGER_2)
        .apiInfo(
            ApiInfoBuilder()
                .title("Bookit API")
                .license("Apache License Version 2.0")
                .licenseUrl("https://github.com/buildit/bookit-api/blob/master/LICENSE")
                .version("1.0")
                .build()
        )
        .ignoredParameterTypes(AuthenticationPrincipal::class.java)
        .directModelSubstitute(ZoneId::class.java, String::class.java)
        .securitySchemes(listOf(
            BasicAuth("spring"),
            OAuthBuilder()
                .name("oauth")
                .grantTypes(listOf(ImplicitGrant(LoginEndpoint("https://login.microsoftonline.com/organizations/oauth2/v2.0/authorize"), "id_token")))
                .scopes(listOf(AuthorizationScope("openid", ""), AuthorizationScope("profile", ""), AuthorizationScope("user.read", "")))
                .build()
        ))
        .select()
        .apis(RequestHandlerSelectors.basePackage(BookitApplication::class.java.`package`.name))
        .build()

    @Bean
    fun securityInfo(): SecurityConfiguration =
        SecurityConfiguration("9a8b8181-afb1-48f8-a839-a895d39f9db0", "", "realm", "9a8b8181-afb1-48f8-a839-a895d39f9db0", "apiKey", ApiKeyVehicle.HEADER, "api_key", " ")
}

/**
 * CORS configuration
 */
@Configuration
class WebMvcConfiguration {
    /**
     * CORS configuration
     */
    @Bean
    fun corsConfigurer(): WebMvcConfigurer = object : WebMvcConfigurerAdapter() {
        override fun addCorsMappings(registry: CorsRegistry) {
            registry
                .addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "HEAD", "POST", "DELETE")
        }
    }
}

@Configuration
class WebSecurityConfiguration(private val props: BookitProperties) {
    @Bean
    fun securityConfigurer() = @Order(SecurityProperties.ACCESS_OVERRIDE_ORDER) object : WebSecurityConfigurerAdapter() {
        override fun configure(security: HttpSecurity) {
            security.cors()
            security.httpBasic()
            // we are using token based authentication. csrf is not required.
            security.csrf().disable()
            security.sessionManagement().sessionCreationPolicy(STATELESS)
            if (props.requireSsl)
                security.requiresChannel().anyRequest().requiresSecure()

            security.authorizeRequests().antMatchers(
                "/",
                "/index.html",
                // these are just swagger stuffs
                "/swagger-ui.html",
                "/swagger-resources/**",
                "/webjars/springfox-swagger-ui/**",
                "/api-docs/**",
                "/v2/api-docs",
                "/configuration/ui",
                "/configuration/security"
            ).permitAll()

            // we only host RESTful API and every services are protected.
            security.authorizeRequests().antMatchers("/v1/ping").permitAll()
            security.authorizeRequests().anyRequest().authenticated()

            security.addFilterBefore(
                JwtAuthenticationFilter(authenticationManager(),
                    OpenIdAuthenticator(props)),
                BasicAuthenticationFilter::class.java)
        }
    }
}

/**
 * Main entry point of the application
 *
 */
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    SpringApplication.run(BookitApplication::class.java, *args)
}

