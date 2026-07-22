package com.example.planit.user.domain

import com.example.planit.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

@Entity
@Table(name = "study_profiles")
class StudyProfile(
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    var user: UserAccount,
    @Column(nullable = false)
    var usualStudyHoursPerDay: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var preferredStudyMethod: PreferredStudyMethod,
) : BaseEntity() {
}
