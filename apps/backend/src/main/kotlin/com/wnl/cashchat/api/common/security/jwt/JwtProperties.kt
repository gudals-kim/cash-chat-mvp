package com.wnl.cashchat.api.common.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secretKey: String,
    val accessTokenExpiry: Long,
    val refreshTokenExpiry: Long
)
