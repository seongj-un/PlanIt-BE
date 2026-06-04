package com.example.planit.plan.domain

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
@Table(name = "completion_events")
class CompletionEvent(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_plan_item_id", nullable = false)
    var dailyPlanItem: DailyPlanItem,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var eventType: CompletionEventType,
    @Column(nullable = false)
    var sproutDelta: Int = 0,
) : BaseEntity() {
}
