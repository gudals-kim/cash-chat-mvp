package com.wnl.cashchat.api.domain.auth.service

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.auth.oauth.model.OAuthUserInfo
import com.wnl.cashchat.api.domain.auth.oauth.properties.OAuthProperties
import com.wnl.cashchat.api.domain.auth.oauth.util.OAuthUserInfoExtractor
import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.auth.persistence.entity.RefreshToken
import com.wnl.cashchat.api.domain.auth.persistence.repository.RefreshTokenRepository
import com.wnl.cashchat.api.domain.auth.web.response.AuthResponse
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import java.time.LocalDateTime
import java.util.*


@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtTokenHandler: JwtTokenHandler,
    private val oAuthProperties: OAuthProperties,
    private val restClient: RestClient,
    oAuthUserInfoExtractors: List<OAuthUserInfoExtractor>
) {

    private val extractorMap = oAuthUserInfoExtractors.associateBy { it.providerType }

    @Transactional
    fun loginAsGuest(deviceToken: String): AuthResponse {

        val user = userRepository.findByDeviceToken(deviceToken)
            ?: userRepository.save(User(name = "Guest", deviceToken = deviceToken))

        val accessToken = jwtTokenHandler.createAccessToken(user.id, user.role)

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = null,
            userId = user.id,
            role = user.role.name
        )

    }

    @Transactional
    fun loginWithOAuth(
        registrationName: String,
        providerType: AuthProviderType,
        code: String,
        deviceToken: String?
    ): AuthResponse {

        val tokenResponse = exchangeAuthCodeForAccessToken(registrationName, code)

        val accessToken = tokenResponse["access_token"] as? String
            ?: throw IllegalStateException("OAuth token response does not contain 'access_token'")

        val rawUserInfo = fetchUserInfo(registrationName, accessToken)

        val extractor = extractorMap[providerType]
            ?: throw IllegalStateException("No OAuthUserInfoExtractor registered for provider: $providerType")

        val userInfo = extractor.extract(rawUserInfo)

        val user = lookupOrRegisterUser(userInfo, providerType, deviceToken)

        return buildAuthResponse(user)
    }

    // -- Exchange Auth Code For Access Token
    // -- & Fetch User Info From Open Authentication Provider

    @Suppress("UNCHECKED_CAST")
    private fun exchangeAuthCodeForAccessToken(registrationName: String, code: String): Map<String, Any> {

        val registration = oAuthProperties.getRegistration(registrationName)
        val provider = oAuthProperties.getProvider(registration.provider)

        return restClient.post()
            .uri(provider.tokenUri)
            .body(
                mapOf(
                    "code" to code,
                    "client_id" to registration.clientId,
                    "client_secret" to registration.clientSecret,
                    "redirect_uri" to registration.redirectUri,
                    "grant_type" to "authorization_code"
                )
            )
            .retrieve()
            .body(Map::class.java) as Map<String, Any>

    }

    @Suppress("UNCHECKED_CAST")
    private fun fetchUserInfo(registrationName: String, accessToken: String): Map<String, Any> {

        val registration = oAuthProperties.getRegistration(registrationName)
        val provider = oAuthProperties.getProvider(registration.provider)

        return restClient.get()
            .uri(provider.userInfoUri)
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(Map::class.java) as Map<String, Any>

    }

    // -- Look Up or Register User --

    private fun lookupOrRegisterUser(
        userInfo: OAuthUserInfo,
        providerType: AuthProviderType,
        deviceToken: String?
    ): User {

        val existingUser = userRepository.findByProviderAndProviderId(providerType, userInfo.providerId)
        if (existingUser != null) return existingUser

        val guestUser = deviceToken?.let { userRepository.findByDeviceToken(it) }

        if (guestUser != null && guestUser.provider == AuthProviderType.NONE) {
            return userRepository.save(guestUser.apply {
                email = userInfo.email
                name = userInfo.name
                profileImageUrl = userInfo.profileImageUrl
                provider = providerType
                providerId = userInfo.providerId
                role = Role.MEMBER
            })
        }

        return userRepository.save(
            User(
                email = userInfo.email,
                name = userInfo.name,
                profileImageUrl = userInfo.profileImageUrl,
                provider = providerType,
                providerId = userInfo.providerId,
                role = Role.MEMBER
            )
        )
    }

    // -- Build Authorization Response --

    private fun buildAuthResponse(user: User): AuthResponse {

        val accessToken = jwtTokenHandler.createAccessToken(user.id, user.role)
        val refreshToken = generateRefreshToken(user.id)

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = user.id,
            role = user.role.name
        )

    }

    // -- Generate Refresh Token --

    private fun generateRefreshToken(userId: Long): String {

        val token = UUID.randomUUID().toString()

        refreshTokenRepository.save(
            RefreshToken(
                userId = userId,
                token = token,
                expiresAt = LocalDateTime.now().plusDays(14)
            )
        )

        return token
    }

    // -- Reissue Access Token Using Refresh Token --

    @Transactional
    fun reissueToken(refreshToken: String): AuthResponse {

        val storedToken = refreshTokenRepository.findByToken(refreshToken)
            ?: throw IllegalArgumentException("Invalid refresh token")

        if (storedToken.expiresAt.isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(storedToken)
            throw IllegalArgumentException("Refresh token expired")
        }

        val user = userRepository.findById(storedToken.userId).orElseThrow {
            IllegalArgumentException("User not found")
        }

        refreshTokenRepository.delete(storedToken)

        return buildAuthResponse(user)

    }
}
