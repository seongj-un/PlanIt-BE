package com.example.planit.plan.domain

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
@Table(name = "plan_generation_jobs")
class PlanGenerationJob(
    @Column(name = "job_id", nullable = false, unique = true, length = 40)
    var jobId: String,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserAccount,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PlanGenerationJobStatus = PlanGenerationJobStatus.PENDING,
    @Column(nullable = false)
    var progressPercent: Int = 0,
    @Column(nullable = false, length = 255)
    var message: String = "오늘 플랜 생성을 준비하고 있어요.",
    @Column
    var planDate: LocalDate? = null,
    @Column
    var generatedPlanId: Long? = null,
) : BaseEntity() {
}
