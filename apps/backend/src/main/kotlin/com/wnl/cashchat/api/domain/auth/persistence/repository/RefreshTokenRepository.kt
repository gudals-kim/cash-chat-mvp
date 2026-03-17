package com.wnl.cashchat.api.domain.auth.persistence.repository

import com.wnl.cashchat.api.domain.auth.persistence.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByToken(token: String): RefreshToken?
    fun deleteByUserId(userId: Long)

    `@Modifying`
    `@Query`("DELETE FROM RefreshToken r WHERE r.token = :token")
    fun deleteByTokenReturningCount(`@Param`("token") token: String): Int
}
}
