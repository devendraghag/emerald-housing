package com.emerald.member.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class MemberSecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    BackendSecurityContextFilter backendSecurityContextFilter(GatewaySessionValidationClient validator,
            SecurityErrorWriter errors) {
        return new BackendSecurityContextFilter(validator, errors);
    }

    @Bean
    CookieCsrfValidationFilter cookieCsrfValidationFilter(SecurityErrorWriter errors) {
        return new CookieCsrfValidationFilter(errors);
    }

    @Bean
    FilterRegistrationBean<BackendSecurityContextFilter> disableServletRegistration(BackendSecurityContextFilter filter) {
        FilterRegistrationBean<BackendSecurityContextFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<CookieCsrfValidationFilter> disableCsrfServletRegistration(CookieCsrfValidationFilter filter) {
        FilterRegistrationBean<CookieCsrfValidationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain memberSecurityFilterChain(HttpSecurity http, BackendSecurityContextFilter identityFilter,
            CookieCsrfValidationFilter csrfFilter, SecurityErrorWriter securityErrors) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // The custom double-submit filter below performs CSRF checks using the shared Gateway token.
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus",
                                "/internal/auth/credentials").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> securityErrors.write(
                                request, response, org.springframework.http.HttpStatus.UNAUTHORIZED,
                                "UNAUTHENTICATED", "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) -> securityErrors.write(
                                request, response, org.springframework.http.HttpStatus.FORBIDDEN,
                                "FORBIDDEN", "You do not have permission to perform this operation")))
                .addFilterBefore(identityFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(csrfFilter, identityFilter.getClass());
        return http.build();
    }
}
