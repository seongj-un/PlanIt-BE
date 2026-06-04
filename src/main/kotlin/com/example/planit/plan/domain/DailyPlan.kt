package com.example.planit.plan.domain

import com.example.planit.common.persistence.BaseEntity
import com.example.planit.exam.domain.ExamPlan
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
@Table(name = "daily_plans")
class DailyPlan(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_plan_id", nullable = false)
    var examPlan: ExamPlan,
    @Column(nullable = false)
    var planDate: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: DailyPlanStatus = DailyPlanStatus.PENDING,
) : BaseEntity() {
}
