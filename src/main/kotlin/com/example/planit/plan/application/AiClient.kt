package com.example.planit.plan.application

/**
 * 배치 단계에서 외부 모델을 호출하는 유일한 접점.
 * 무료 모델이 정해지면 이 인터페이스의 구현체를 @Bean 으로 추가하고
 * application.yaml 의 planit.ai.arrangement.mode 를 "ai" 로 바꾼다.
 * 인증키는 환경변수로 주입하고 코드/깃에 넣지 않는다.
 */
fun interface AiClient {
    fun complete(prompt: String): String
}
