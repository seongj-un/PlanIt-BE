package com.example.planit.user.domain

import com.example.planit.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "user_accounts")
class UserAccount(
    @Column(nullable = false, length = 50)
    var name: String,
    @Column(nullable = false, unique = true, length = 120)
    var email: String,
    @Column(nullable = false, length = 255)
    var passwordHash: String,
    @Column(nullable = false)
    var onboardingCompleted: Boolean = false,
) : BaseEntity() {
}
