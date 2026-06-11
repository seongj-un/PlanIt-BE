package com.example.planit.user.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface NotificationSettingsRepository : JpaRepository<NotificationSettings, Long> {
    fun findByUserId(userId: Long): Optional<NotificationSettings>
}
