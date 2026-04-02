package com.imoil.formula.engine;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.imoil.formula.engine.functions.WindowAvgFunction;
import com.imoil.formula.engine.functions.WindowMaxFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RuleEngineConfig {

    @Bean
    public AviatorEvaluatorInstance aviatorEvaluatorInstance(
            WindowAvgFunction windowAvgFunction,
            WindowMaxFunction windowMaxFunction) {
        
        // 싱글톤에 의존하지 않는 전용 엔진 인스턴스 생성
        AviatorEvaluatorInstance instance = AviatorEvaluator.newInstance();
        
        // 메모리 절약과 컴파일 오버헤드 방지를 위해 결과 캐싱 활성화 (자체 내부 캐시 의존)
        instance.setCachedExpressionByDefault(true);
        
        // 커스텀 윈도우 함수 바인딩
        instance.addFunction(windowAvgFunction);
        instance.addFunction(windowMaxFunction);
        
        return instance;
    }
}
