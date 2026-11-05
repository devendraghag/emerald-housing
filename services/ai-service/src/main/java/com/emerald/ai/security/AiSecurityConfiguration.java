package com.emerald.ai.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class AiSecurityConfiguration {
    @Bean
    BackendSecurityContextFilter backendSecurityContextFilter(GatewaySessionValidationClient validator, SecurityErrorWriter errors) {
        return new BackendSecurityContextFilter(validator, errors);
    }

    @Bean
    CookieCsrfValidationFilter cookieCsrfValidationFilter(SecurityErrorWriter errors) {
        return new CookieCsrfValidationFilter(errors);
    }

    @Bean
    FilterRegistrationBean<BackendSecurityContextFilter> disableIdentityServletRegistration(BackendSecurityContextFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<CookieCsrfValidationFilter> disableCsrfServletRegistration(CookieCsrfValidationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain aiSecurityFilterChain(HttpSecurity http, BackendSecurityContextFilter identityFilter,
            CookieCsrfValidationFilter csrfFilter, SecurityErrorWriter errors) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable()).httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable()).logout(logout -> logout.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, cause) -> errors.write(request, response,
                                org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"))
                        .accessDeniedHandler((request, response, cause) -> errors.write(request, response,
                                org.springframework.http.HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this operation")))
                .addFilterBefore(identityFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(csrfFilter, BackendSecurityContextFilter.class);
        return http.build();
    }
}
