package com.imoil.formula.engine;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.imoil.formula.engine.functions.WindowAvgFunction;
import com.imoil.formula.engine.functions.WindowMaxFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineServiceTest {

    private RuleEngineService ruleEngineService;

    @BeforeEach
    void setUp() {
        AviatorEvaluatorInstance instance = AviatorEvaluator.newInstance();
        instance.setCachedExpressionByDefault(true);
        instance.addFunction(new WindowAvgFunction());
        instance.addFunction(new WindowMaxFunction());
        
        ruleEngineService = new RuleEngineService(instance);
    }

    @Test
    void testBasicArithmetic() {
        Map<String, Object> env = new HashMap<>();
        env.put("value", 10.5);
        double result = ruleEngineService.evaluate("value * 2.0 + 10", env);
        assertThat(result).isEqualTo(31.0);
    }

    @Test
    void testWindowFunctionsWithEnv() {
        Map<String, Object> env = new HashMap<>();
        env.put("value", 10.0);
        // 테스트용 히스토리 주입
        env.put("window_data", Arrays.asList(2.0, 4.0, 6.0, 8.0, 10.0));
        
        // window_avg(3)은 마지막 3개 [6.0, 8.0, 10.0]의 평균을 구함 (8.0)
        double avgResult = ruleEngineService.evaluate("value + window_avg(3)", env);
        assertThat(avgResult).isEqualTo(18.0);
        
        // window_max(4)는 마지막 4개 [4.0, 6.0, 8.0, 10.0]의 최댓값을 구함 (10.0)
        double maxResult = ruleEngineService.evaluate("window_max(4) * 2", env);
        assertThat(maxResult).isEqualTo(20.0);
    }
}
