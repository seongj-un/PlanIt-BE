package com.example.planit.auth.domain

import com.example.planit.common.persistence.BaseEntity
import com.example.planit.user.domain.UserAccount
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserAccount,
    @Column(nullable = false, unique = true, length = 64)
    var tokenId: String,
    @Column(nullable = false)
    var expiresAt: LocalDateTime,
    @Column(nullable = false)
    var revoked: Boolean = false,
) : BaseEntity()
