package com.example.planit.plan.domain

import com.example.planit.common.persistence.BaseEntity
import com.example.planit.exam.domain.SubjectScope
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "daily_plan_items")
class DailyPlanItem(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_plan_id", nullable = false)
    var dailyPlan: DailyPlan,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_scope_id", nullable = false)
    var subjectScope: SubjectScope,
    @Column(nullable = false, length = 50)
    var subjectNameSnapshot: String,
    @Column(nullable = false, length = 500)
    var rangeTextSnapshot: String,
    @Column(nullable = false, length = 255)
    var studyMethodSnapshot: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var priority: PlanItemPriority = PlanItemPriority.MEDIUM,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PlanItemStatus = PlanItemStatus.PENDING,
    @Column(nullable = false)
    var plannedUnits: Int,
    @Column(nullable = false)
    var estimatedMinutes: Int,
    @Column(nullable = false)
    var manuallyAdjusted: Boolean = false,
) : BaseEntity() {
}
