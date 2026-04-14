package com.barley.controller;

import com.barley.dto.AuthRequest;
import com.barley.dto.AuthResponse;
import com.barley.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

/**
 * Handles authentication — the only public endpoint in the API.
 *
 * <p>Flow:
 * <ol>
 *   <li>Client POSTs credentials.</li>
 *   <li>Spring's {@link AuthenticationManager} verifies them (BCrypt compare).</li>
 *   <li>On success, generate a signed JWT and return it.</li>
 *   <li>Client includes the JWT in subsequent requests as {@code Authorization: Bearer <token>}.</li>
 * </ol>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Obtain a JWT bearer token")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService    userDetailsService;
    private final JwtService            jwtService;

    @Value("${barley.security.jwt.expiration-ms}")
    private long expirationMs;

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive a JWT token",
               description = "Use credentials: admin/admin123 (ADMIN) or user/user123 (USER)")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        // Throws AuthenticationException (-> 401) on bad credentials
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        String token = jwtService.generateToken(userDetails);

        return ResponseEntity.ok(new AuthResponse(token, expirationMs));
    }
}
