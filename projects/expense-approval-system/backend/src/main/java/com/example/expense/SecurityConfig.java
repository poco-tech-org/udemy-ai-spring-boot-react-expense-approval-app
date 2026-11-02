package com.example.expense;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain security(HttpSecurity http, @Value("${app.origin}") String origin)
      throws Exception {
    http.authorizeHttpRequests(
            a -> a.requestMatchers("/api/csrf", "/error").permitAll().anyRequest().authenticated())
        .exceptionHandling(
            e ->
                e.defaultAuthenticationEntryPointFor(
                        (req, res, ex) -> {
                          res.setStatus(401);
                          res.setContentType("application/json;charset=UTF-8");
                          res.getWriter().write("{\"detail\":\"ログインが必要です。\"}");
                        },
                        req -> req.getRequestURI().startsWith("/api/"))
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          res.setStatus(403);
                          res.setContentType("application/json;charset=UTF-8");
                          res.getWriter().write("{\"detail\":\"操作が許可されていません。画面を再読み込みしてください。\"}");
                        }))
        .oauth2Login(o -> o.defaultSuccessUrl(origin + "/", true))
        .logout(
            l ->
                l.logoutSuccessUrl(origin + "/")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID"));
    return http.build();
  }
}
