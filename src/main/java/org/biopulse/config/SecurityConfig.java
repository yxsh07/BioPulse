package org.biopulse.config;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration c) throws Exception {
    return c.getAuthenticationManager();
  }

  @Bean
  SecurityFilterChain filter(
      HttpSecurity http, @Value("${biopulse.allowed-origins}") String origins) throws Exception {
    var csrf = new CookieCsrfTokenRepository();
    csrf.setCookieHttpOnly(false);
    http.cors(
            c ->
                c.configurationSource(
                    request -> {
                      var x = new CorsConfiguration();
                      x.setAllowedOrigins(Arrays.asList(origins.split(",")));
                      x.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
                      x.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Authorization"));
                      x.setAllowCredentials(true);
                      return x;
                    }))
        .csrf(
            c ->
                c.csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/health",
                        "/api/v1/session",
                        "/",
                        "/index.html",
                        "/assets/**",
                        "/favicon.svg",
                        "/openapi.yaml")
                    .permitAll()
                    .requestMatchers("/api/v1/internal/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/**", "/ws/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    (req, res, ex) -> {
                      res.setStatus(401);
                      res.setContentType("application/problem+json");
                      res.getWriter()
                          .write("{\"title\":\"Authentication required\",\"status\":401}");
                    }))
        .logout(
            l ->
                l.logoutUrl("/api/v1/logout")
                    .logoutSuccessHandler((req, res, a) -> res.setStatus(204)));
    return http.build();
  }
}
