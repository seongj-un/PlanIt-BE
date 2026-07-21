package com.example.planit.plan.application

import com.example.planit.plan.application.PlanGenerationSupport.normalize
import com.example.planit.plan.domain.PlanItemPriority
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

class AiArrangementProvider(
    private val aiClient: AiClient,
    private val fallback: RuleBasedArrangementProvider,
    private val objectMapper: ObjectMapper,
) : PlanArrangementProvider {

    override fun arrange(schedule: PlanSchedule, context: ArrangementContext): PlanSchedule =
        runCatching { applyAi(schedule, context) }
            .getOrElse { fallback.arrange(schedule, context) }

    private fun applyAi(schedule: PlanSchedule, context: ArrangementContext): PlanSchedule {
        val prompt = buildPrompt(schedule, context)
        val raw = aiClient.complete(prompt)
        val root = objectMapper.readTree(extractJson(raw))
        val daysNode = root["days"] as? ArrayNode ?: error("missing days")

        val arrangementByDate = daysNode.associate { dayNode ->
            val date = dayNode["date"].asText()
            val order = (dayNode["order"] as ArrayNode).map { it.asText() }
            val priorities = (dayNode["priorities"] as? ObjectNode)?.let { node ->
                node.fields().asSequence().associate { it.key to it.value.asText() }
            } ?: emptyMap()
            date to DayArrangement(order, priorities)
        }

        // 날짜 집합 일치 검증
        val scheduleDates = schedule.days.map { it.planDate.toString() }.toSet()
        require(arrangementByDate.keys == scheduleDates) { "day set mismatch" }

        val days = schedule.days.map { day ->
            val arrangement = arrangementByDate.getValue(day.planDate.toString())
            val bySubject = day.items.associateBy { it.subjectName }

            // order 는 그날 과목의 순열이어야 함
            require(arrangement.order.toSet() == bySubject.keys) { "order not a permutation" }

            val ordered = arrangement.order.mapIndexed { index, subjectName ->
                val item = bySubject.getValue(subjectName)
                val priority = arrangement.priorities[subjectName]?.let { parsePriority(it) } ?: item.priority
                item.copy(displayOrder = index, priority = priority)
            }
            day.copy(items = ordered)
        }
        return schedule.copy(days = days)
    }

    private fun parsePriority(raw: String): PlanItemPriority = enumValueOf(raw.trim())

    /** 모델이 코드펜스나 잡텍스트를 섞어 보낼 수 있어 첫 { ~ 마지막 } 만 취한다. */
    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "no json object" }
        return raw.substring(start, end + 1)
    }

    private fun buildPrompt(schedule: PlanSchedule, context: ArrangementContext): String {
        val input = objectMapper.createObjectNode().apply {
            putObject("student").apply {
                context.age?.let { put("age", it) }
                context.schoolLevel?.let { put("schoolLevel", it) }
                put("preferredMethod", context.preferredStudyMethod)
            }
            putArray("subjects").apply {
                context.subjects.forEach { s ->
                    addObject().apply {
                        put("name", s.subjectName)
                        put("difficulty", s.difficulty.name)
                        put("isDifficult", s.isDifficult)
                    }
                }
            }
            putArray("days").apply {
                schedule.days.forEach { day ->
                    addObject().apply {
                        put("date", day.planDate.toString())
                        putArray("items").apply {
                            day.items.forEach { item ->
                                addObject().apply {
                                    put("subject", item.subjectName)
                                    put("units", item.plannedUnits)
                                    put("range", item.rangeText)
                                }
                            }
                        }
                    }
                }
            }
        }
        val inputJson = objectMapper.writeValueAsString(input)
        return """
            너는 학생의 하루 학습 항목을 공부하기 좋은 순서로 배열하는 도우미다.
            아래 입력의 각 날짜에 대해, 그날 항목들을 학습 순서로 재배열하라.
            어려운 과목(isDifficult=true)이나 난이도 HIGH 과목은 집중력이 높은 앞쪽에 둔다.
            규칙:
            - 숫자(units, range)를 절대 바꾸지 마라. 항목을 추가하거나 빼지 마라.
            - 순서(order)와 priority(HIGH|MEDIUM|LOW)만 결정하라.
            - 반드시 아래 형식의 JSON만 출력하라. 다른 텍스트를 붙이지 마라.
            출력 형식:
            { "days": [ { "date": "YYYY-MM-DD", "order": ["과목명", ...], "priorities": { "과목명": "HIGH" } } ] }

            입력:
            $inputJson
        """.trimIndent()
    }

    private data class DayArrangement(
        val order: List<String>,
        val priorities: Map<String, String>,
    )
}
