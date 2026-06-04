package com.example.planit.user.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserAccountRepository : JpaRepository<UserAccount, Long> {
    fun findByEmail(email: String): Optional<UserAccount>
}
