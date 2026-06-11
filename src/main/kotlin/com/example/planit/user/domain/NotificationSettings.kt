package com.example.planit.user.domain

import com.example.planit.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.LocalTime

@Entity
@Table(name = "notification_settings")
class NotificationSettings(
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    var user: UserAccount,
    @Column(nullable = false)
    var dailyReminderEnabled: Boolean,
    @Column
    var dailyReminderTime: LocalTime?,
) : BaseEntity() {
}
