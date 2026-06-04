package com.example.planit.exam.domain

import org.springframework.data.jpa.repository.JpaRepository

interface SubjectScopeRepository : JpaRepository<SubjectScope, Long> {
    fun findAllByExamPlanId(examPlanId: Long): List<SubjectScope>
}
