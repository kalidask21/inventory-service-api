package com.nuuly.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(Customizer.withDefaults());
        http.csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain resourceServerFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health", "/v3/api-docs/**",
                    "/swagger-ui/**", "/swagger-ui.html",
                    "/oauth2/token", "/oauth2/jwks", "/h2-console/**"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/inventory", "/api/inventory/**")
                    .hasAuthority("SCOPE_inventory.read")
                .requestMatchers(HttpMethod.POST, "/api/inventory/**")
                    .hasAuthority("SCOPE_inventory.write")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
