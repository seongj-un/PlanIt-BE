package com.example.planit.user.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface StudyProfileRepository : JpaRepository<StudyProfile, Long> {
    fun findByUserId(userId: Long): Optional<StudyProfile>
}
