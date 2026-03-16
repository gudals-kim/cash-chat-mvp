package com.wnl.cashchat.api.domain.auth.web.response

data class AuthResponse(
    val userId: Long,
    val role: String,
    val accessToken: String,
    val refreshToken: String?
)