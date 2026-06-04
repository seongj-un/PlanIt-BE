package com.example.planit.exam.domain

import com.example.planit.common.persistence.BaseEntity
import com.example.planit.user.domain.UserAccount
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "exam_plans")
class ExamPlan(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserAccount,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var targetExamType: TargetExamType,
    @Column(nullable = false, length = 80)
    var targetExamLabel: String,
    @Column(nullable = false)
    var examDate: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ExamPlanStatus = ExamPlanStatus.ACTIVE,
) : BaseEntity() {
}
