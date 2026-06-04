package com.example.planit.exam.domain

import com.example.planit.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "subject_scopes")
class SubjectScope(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_plan_id", nullable = false)
    var examPlan: ExamPlan,
    @Column(nullable = false, length = 50)
    var subjectName: String,
    @Column(nullable = false, length = 500)
    var rawRangeText: String,
    @Column(nullable = false, length = 255)
    var preferredMethodNote: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var difficulty: DifficultyLevel = DifficultyLevel.MEDIUM,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var unitType: ScopeUnitType = ScopeUnitType.CUSTOM,
    @Column(nullable = false)
    var totalUnits: Int,
    @Column(nullable = false)
    var remainingUnits: Int,
) : BaseEntity() {
}
