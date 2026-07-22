package com.example.planit.user.application

import com.example.planit.auth.security.AuthenticatedUser
import com.example.planit.common.api.CommonApiException
import com.example.planit.exam.domain.ExamPlanRepository
import com.example.planit.exam.domain.ExamPlanStatus
import com.example.planit.plan.application.TodayPlanService
import com.example.planit.plan.domain.CompletionEventRepository
import com.example.planit.user.api.AccountSettingsRequest
import com.example.planit.user.api.NotificationSettingsRequest
import com.example.planit.user.api.StudyProfileRequest
import com.example.planit.user.api.StudySettingsRequest
import com.example.planit.user.api.UpdatedResponse
import com.example.planit.user.api.UserProfileResponse
import com.example.planit.user.domain.NotificationSettings
import com.example.planit.user.domain.NotificationSettingsRepository
import com.example.planit.user.domain.PreferredStudyMethod
import com.example.planit.user.domain.StudyProfile
import com.example.planit.user.domain.StudyProfileRepository
import com.example.planit.user.domain.UserAccountRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalTime
import java.time.format.DateTimeParseException

@Service
class UserProfileService(
    private val userAccountRepository: UserAccountRepository,
    private val studyProfileRepository: StudyProfileRepository,
    private val notificationSettingsRepository: NotificationSettingsRepository,
    private val passwordEncoder: PasswordEncoder,
    private val examPlanRepository: ExamPlanRepository,
    private val completionEventRepository: CompletionEventRepository,
    private val todayPlanService: TodayPlanService,
) {

    @Transactional(readOnly = true)
    fun getMyProfile(principal: AuthenticatedUser): UserProfileResponse {
        val user = userAccountRepository.findById(principal.id)
            .orElseThrow { CommonApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.") }
        val studyProfile = studyProfileRepository.findByUserId(user.id!!)
            .orElse(null)
        val activePlan = examPlanRepository.findByUserIdAndStatus(user.id!!, ExamPlanStatus.ACTIVE)
            .orElse(null)
        val sproutCount = completionEventRepository.findAllByDailyPlanItemDailyPlanExamPlanUserId(user.id!!).sumOf { it.sproutDelta }

        return UserProfileResponse(
            id = user.id!!,
            name = user.name,
            email = user.email,
            targetExamType = activePlan?.targetExamType?.name,
            targetExamLabel = activePlan?.targetExamLabel,
            examDate = activePlan?.examDate,
            usualStudyHoursPerDay = studyProfile?.usualStudyHoursPerDay,
            preferredStudyMethod = studyProfile?.preferredStudyMethod?.name,
            sproutCount = sproutCount,
            attendanceStreakDays = todayPlanService.streakDays(user.id!!),
            onboardingCompleted = user.onboardingCompleted,
        )
    }

    @Transactional
    fun upsertStudyProfile(principal: AuthenticatedUser, request: StudyProfileRequest): UserProfileResponse {
        require(request.usualStudyHoursPerDay in 1..24) { "하루 공부 시간은 1 이상 24 이하여야 합니다." }

        val user = userAccountRepository.findById(principal.id)
            .orElseThrow { CommonApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.") }
        val preferredStudyMethod = enumValueOf<PreferredStudyMethod>(request.preferredStudyMethod)

        val profile = studyProfileRepository.findByUserId(user.id!!)
            .orElseGet {
                StudyProfile(
                    user = user,
                    usualStudyHoursPerDay = request.usualStudyHoursPerDay,
                    preferredStudyMethod = preferredStudyMethod,
                )
            }

        profile.usualStudyHoursPerDay = request.usualStudyHoursPerDay
        profile.preferredStudyMethod = preferredStudyMethod

        studyProfileRepository.save(profile)
        return getMyProfile(principal)
    }

    @Transactional
    fun updateStudySettings(principal: AuthenticatedUser, request: StudySettingsRequest): UpdatedResponse {
        if (request.usualStudyHoursPerDay !in 1..24) {
            throw CommonApiException(HttpStatus.BAD_REQUEST, "INVALID_STUDY_HOURS", "하루 공부 시간은 1 이상 24 이하여야 합니다.")
        }

        val user = userAccountRepository.findById(principal.id)
            .orElseThrow { CommonApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.") }
        val profile = studyProfileRepository.findByUserId(user.id!!)
            .orElseThrow {
                CommonApiException(HttpStatus.CONFLICT, "STUDY_PROFILE_REQUIRED", "공부 설정을 수정하려면 학습 프로필을 먼저 입력해주세요.")
            }

        profile.usualStudyHoursPerDay = request.usualStudyHoursPerDay
        profile.preferredStudyMethod = parsePreferredStudyMethod(request.preferredStudyMethod)
        return UpdatedResponse(updated = true)
    }

    @Transactional
    fun updateNotificationSettings(principal: AuthenticatedUser, request: NotificationSettingsRequest): UpdatedResponse {
        val user = userAccountRepository.findById(principal.id)
            .orElseThrow { CommonApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.") }

        val reminderTime = parseReminderTime(request.dailyReminderEnabled, request.dailyReminderTime)
        val settings = notificationSettingsRepository.findByUserId(user.id!!)
            .orElseGet {
                NotificationSettings(
                    user = user,
                    dailyReminderEnabled = request.dailyReminderEnabled,
                    dailyReminderTime = reminderTime,
                )
            }

        settings.dailyReminderEnabled = request.dailyReminderEnabled
        settings.dailyReminderTime = reminderTime
        notificationSettingsRepository.save(settings)
        return UpdatedResponse(updated = true)
    }

    @Transactional
    fun updateAccount(principal: AuthenticatedUser, request: AccountSettingsRequest): UpdatedResponse {
        val user = userAccountRepository.findById(principal.id)
            .orElseThrow { CommonApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.") }

        val hasName = request.name != null
        val hasPassword = request.password != null
        if (!hasName && !hasPassword) {
            throw CommonApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_UPDATE_EMPTY", "수정할 계정 정보가 없습니다.")
        }

        request.name?.let { rawName ->
            val normalizedName = rawName.trim()
            if (normalizedName.isBlank()) {
                throw CommonApiException(HttpStatus.BAD_REQUEST, "NAME_REQUIRED", "이름은 비어 있을 수 없습니다.")
            }
            if (normalizedName.length > 50) {
                throw CommonApiException(HttpStatus.BAD_REQUEST, "NAME_TOO_LONG", "이름은 50자 이하여야 합니다.")
            }
            user.name = normalizedName
        }

        request.password?.let { rawPassword ->
            if (rawPassword.isBlank()) {
                throw CommonApiException(HttpStatus.BAD_REQUEST, "PASSWORD_REQUIRED", "비밀번호는 비어 있을 수 없습니다.")
            }
            user.passwordHash = passwordEncoder.encode(rawPassword)
        }

        return UpdatedResponse(updated = true)
    }

    private fun parsePreferredStudyMethod(value: String): PreferredStudyMethod =
        runCatching { enumValueOf<PreferredStudyMethod>(value.trim()) }
            .getOrElse {
                throw CommonApiException(HttpStatus.BAD_REQUEST, "INVALID_PREFERRED_STUDY_METHOD", "유효하지 않은 공부 방법입니다.")
            }

    private fun parseReminderTime(enabled: Boolean, rawTime: String?): LocalTime? {
        val normalized = rawTime?.trim()?.takeIf { it.isNotEmpty() }
        if (enabled && normalized == null) {
            throw CommonApiException(HttpStatus.BAD_REQUEST, "DAILY_REMINDER_TIME_REQUIRED", "알림을 켜려면 시간을 입력해주세요.")
        }
        if (normalized == null) {
            return null
        }

        return try {
            LocalTime.parse(normalized)
        } catch (_: DateTimeParseException) {
            throw CommonApiException(HttpStatus.BAD_REQUEST, "INVALID_DAILY_REMINDER_TIME", "알림 시간은 HH:mm 형식이어야 합니다.")
        }
    }
}
