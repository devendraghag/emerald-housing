package com.emerald.society.security;

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
public class SocietySecurityConfiguration {
    @Bean
    CookieCsrfValidationFilter societyCsrfValidationFilter(SecurityErrorWriter errors) {
        return new CookieCsrfValidationFilter(errors);
    }

    @Bean
    FilterRegistrationBean<RequestIdentityFilter> disableServletIdentityFilterRegistration(RequestIdentityFilter filter) {
        FilterRegistrationBean<RequestIdentityFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<CookieCsrfValidationFilter> disableServletCsrfFilterRegistration(CookieCsrfValidationFilter filter) {
        FilterRegistrationBean<CookieCsrfValidationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain societySecurityFilterChain(HttpSecurity http, RequestIdentityFilter identityFilter,
            CookieCsrfValidationFilter csrfFilter, SecurityErrorWriter securityErrors) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/metrics", "/actuator/metrics/**").hasAnyRole("ADMIN", "SOCIETY_ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> securityErrors.write(request, response,
                                org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) -> securityErrors.write(request, response,
                                org.springframework.http.HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this operation")))
                .addFilterBefore(identityFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(csrfFilter, RequestIdentityFilter.class);
        return http.build();
    }
}
