package com.imoil.formula.engine;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Feature;
import com.googlecode.aviator.Options;
import com.imoil.formula.engine.functions.WindowAvgFunction;
import com.imoil.formula.engine.functions.WindowMaxFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Configuration
public class RuleEngineConfig {

    @Bean
    public AviatorEvaluatorInstance aviatorEvaluatorInstance(
            WindowAvgFunction windowAvgFunction,
            WindowMaxFunction windowMaxFunction) {
        
        // 싱글톤에 의존하지 않는 전용 엔진 인스턴스 생성
        AviatorEvaluatorInstance instance = AviatorEvaluator.newInstance();
        
        // 메모리 폭발 누수(OOM) 방지를 위한 JIT 바이트코드 컴파일 결과 캐싱 활성화
        instance.setCachedExpressionByDefault(true);

        // 보안 샌드박스 설정 (보안 취약점 방지: Code Injection 및 DoS 차단)
        // 1. 임의 Java 클래스 접근 차단 (화이트리스트 빈 집합 적용)
        instance.setOption(Options.ALLOWED_CLASS_SET, Collections.emptySet());

        // 2. 위험한 언어 기능 비활성화
        Set<Feature> safeFeatures = EnumSet.allOf(Feature.class);
        safeFeatures.remove(Feature.NewInstance);
        safeFeatures.remove(Feature.StaticMethods);
        safeFeatures.remove(Feature.StaticFields);
        safeFeatures.remove(Feature.Use);
        safeFeatures.remove(Feature.Module);
        safeFeatures.remove(Feature.ExceptionHandle);
        safeFeatures.remove(Feature.Assignment);
        safeFeatures.remove(Feature.Let);
        safeFeatures.remove(Feature.Lambda);
        safeFeatures.remove(Feature.Fn);
        safeFeatures.remove(Feature.ForLoop);
        safeFeatures.remove(Feature.WhileLoop);
        safeFeatures.remove(Feature.Return);
        safeFeatures.remove(Feature.StringInterpolation);
        instance.setOption(Options.FEATURE_SET, safeFeatures);

        // 3. 루프 횟수 제한 및 실행 타임아웃 설정 (DoS 방지)
        instance.setOption(Options.MAX_LOOP_COUNT, 1000);
        instance.setOption(Options.EVAL_TIMEOUT_MS, 5000L);
        
        // 커스텀 윈도우 함수 바인딩
        instance.addFunction(windowAvgFunction);
        instance.addFunction(windowMaxFunction);
        
        return instance;
    }
}
