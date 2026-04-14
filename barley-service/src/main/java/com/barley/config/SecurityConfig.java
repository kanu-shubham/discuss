package com.barley.config;

import com.barley.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * JWT-based stateless security configuration.
 *
 * <p><b>Interview talking points:</b>
 * <ul>
 *   <li>Stateless (STATELESS session policy) = no HttpSession, JWT carries all auth info.</li>
 *   <li>BCrypt work-factor of 12 balances security vs. CPU cost (~300ms per hash on typical hardware).</li>
 *   <li>{@code @EnableMethodSecurity} enables {@code @PreAuthorize} / {@code @PostAuthorize} at
 *       the service layer for fine-grained access control.</li>
 *   <li>CSRF disabled because REST APIs use stateless JWT tokens, not session cookies.</li>
 *   <li>In production: UserDetailsService loads users from DB, not in-memory.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    private static final String[] PUBLIC_ENDPOINTS = {
        "/auth/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/actuator/health",
        "/actuator/info",
        "/h2-console/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)       // disabled for stateless JWT API
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers(HttpMethod.GET,  "/barley/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/barley/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,  "/barley/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,"/barley/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            // JWT filter runs before Spring's username/password filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // Allow H2 console to render frames (dev only)
            .headers(h -> h.frameOptions(f -> f.sameOrigin()));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * In-memory users for development.
     * In production: implement UserDetailsService backed by a users table.
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
            User.builder()
                .username("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN")
                .build(),
            User.builder()
                .username("user")
                .password(encoder.encode("user123"))
                .roles("USER")
                .build()
        );
    }

    @Bean
    public AuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
