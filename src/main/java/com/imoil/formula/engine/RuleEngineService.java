package com.imoil.formula.engine;

import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class RuleEngineService {

    private final AviatorEvaluatorInstance evaluator;

    /**
     * 런타임 수식과 컨텍스트(Map)를 기반으로 결과를 도출합니다.
     * 표현식은 내부적으로 컴파일되어 LRU 캐싱됩니다. (GC 오버헤드 최소화)
     */
    public double evaluate(String expressionText, Map<String, Object> env) {
        Expression expression = evaluator.compile(expressionText, true); // true = Use Cache
        Object result = expression.execute(env);
        
        if (result instanceof Number) {
            return ((Number) result).doubleValue();
        }
        
        throw new IllegalArgumentException("The evaluation result could not be cast to a numeric value.");
    }
}
