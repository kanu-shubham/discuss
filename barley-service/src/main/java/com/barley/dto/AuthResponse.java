package com.barley.dto;

public record AuthResponse(String token, String type, long expiresIn) {
    public AuthResponse(String token, long expiresIn) {
        this(token, "Bearer", expiresIn);
    }
}
