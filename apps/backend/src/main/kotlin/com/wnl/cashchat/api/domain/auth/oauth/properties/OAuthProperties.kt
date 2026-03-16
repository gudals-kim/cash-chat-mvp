package com.wnl.cashchat.api.domain.auth.oauth.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "oauth")
data class OAuthProperties(
    val registration: Map<String, RegistrationProperties> = emptyMap(),
    val provider: Map<String, ProviderProperties> = emptyMap()
) {

    data class RegistrationProperties(
        val provider: String,
        val clientId: String,
        val clientSecret: String,
        val redirectUri: String
    )

    data class ProviderProperties(
        val tokenUri: String,
        val userInfoUri: String
    )

    fun getRegistration(name: String): RegistrationProperties =
        registration[name]
            ?: throw kotlin.IllegalStateException("OAuth registration '$name' is not configured")

    fun getProvider(name: String): ProviderProperties =
        provider[name]
            ?: throw kotlin.IllegalStateException("OAuth provider '$name' is not configured")
}