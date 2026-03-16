package com.wnl.cashchat.api.domain.user.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import jakarta.persistence.*

@Entity
@Table(name = "users")
class User(

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    //

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: Role = Role.GUEST,

    //

    @Column(unique = true)
    var deviceToken: String? = null,

    //

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var provider: AuthProviderType = AuthProviderType.NONE,

    var providerId: String? = null,

    //

    var email: String? = null,

    @Column(nullable = false)
    var name: String,

    var profileImageUrl: String? = null,

) : BaseEntity()
