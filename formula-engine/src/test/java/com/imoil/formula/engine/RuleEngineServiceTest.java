package com.imoil.formula.engine;

import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.imoil.formula.engine.functions.WindowAvgFunction;
import com.imoil.formula.engine.functions.WindowMaxFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleEngineServiceTest {

    private RuleEngineService ruleEngineService;

    @BeforeEach
    void setUp() {
        RuleEngineConfig config = new RuleEngineConfig();
        AviatorEvaluatorInstance instance = config.aviatorEvaluatorInstance(
                new WindowAvgFunction(),
                new WindowMaxFunction()
        );
        ruleEngineService = new RuleEngineService(instance);
    }

    @Test
    @DisplayName("기본 연산 수식 평가 정상 동작 테스트")
    void testBasicArithmetic() {
        Map<String, Object> env = new HashMap<>();
        env.put("value", 10.5);
        double result = ruleEngineService.evaluate("value * 2.0 + 10", env);
        assertThat(result).isEqualTo(31.0);
    }

    @Test
    @DisplayName("삼항 연산자 및 조건식 평가 정상 동작 테스트")
    void testTernaryExpression() {
        Map<String, Object> env = new HashMap<>();
        env.put("value", 60.0);
        double result = ruleEngineService.evaluate("value > 50 ? value * 1.1 : value", env);
        assertThat(result).isEqualTo(66.0);
    }

    @Test
    @DisplayName("윈도우 커스텀 함수(window_avg, window_max) 평가 정상 동작 테스트")
    void testWindowFunctionsWithEnv() {
        Map<String, Object> env = new HashMap<>();
        env.put("value", 10.0);
        env.put("window_data", Arrays.asList(2.0, 4.0, 6.0, 8.0, 10.0));

        double avgResult = ruleEngineService.evaluate("value + window_avg(3)", env);
        assertThat(avgResult).isEqualTo(18.0);

        double maxResult = ruleEngineService.evaluate("window_max(4) * 2", env);
        assertThat(maxResult).isEqualTo(20.0);
    }

    @Test
    @DisplayName("윈도우 커스텀 함수(window_avg, window_max) 빈 환경(window_data 부재) 평가 테스트")
    void testWindowFunctionsWithEmptyEnv() {
        Map<String, Object> env = new HashMap<>();
        // window_data is missing, which is handled as null

        double avgResult = ruleEngineService.evaluate("window_avg(3)", env);
        assertThat(avgResult).isEqualTo(0.0);

        double maxResult = ruleEngineService.evaluate("window_max(3)", env);
        assertThat(maxResult).isEqualTo(0.0);
    }

    @Test
    @DisplayName("null 또는 빈 문자열 표현식 입력 시 IllegalArgumentException 발생")
    void testInvalidExpressionInputs() {
        Map<String, Object> env = new HashMap<>();
        assertThatThrownBy(() -> ruleEngineService.evaluate(null, env))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ruleEngineService.evaluate("", env))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ruleEngineService.evaluate("   ", env))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("보안: 객체 생성(new 인스턴스화) 시도 차단")
    void testMaliciousNewInstance() {
        Map<String, Object> env = new HashMap<>();
        assertThatThrownBy(() -> ruleEngineService.evaluate("new java.lang.ProcessBuilder('ls').start()", env))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("보안: 클래스 정적 메서드(Static Methods) 및 Reflection 호출 차단")
    void testMaliciousStaticMethodAndReflection() {
        Map<String, Object> env = new HashMap<>();
        assertThatThrownBy(() -> ruleEngineService.evaluate("java.lang.System.exit(0)", env))
                .isInstanceOf(Exception.class);

        assertThatThrownBy(() -> ruleEngineService.evaluate("Class.forName('java.lang.Runtime').getRuntime()", env))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("보안: 정적 필드(Static Fields) 접근 차단")
    void testMaliciousStaticFieldAccess() {
        Map<String, Object> env = new HashMap<>();
        assertThatThrownBy(() -> ruleEngineService.evaluate("java.io.File.separator", env))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("보안: use / module 임포트 구문 차단")
    void testMaliciousUseImport() {
        Map<String, Object> env = new HashMap<>();
        assertThatThrownBy(() -> ruleEngineService.evaluate("use java.lang.System; System.currentTimeMillis()", env))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("보안: 무한 루프 DoS 시도 차단 (MAX_LOOP_COUNT 초과)")
    void testLoopCountRestriction() {
        Map<String, Object> env = new HashMap<>();
        assertThatThrownBy(() -> ruleEngineService.evaluate("let i = 0; while(true) { i = i + 1; } return i;", env))
                .isInstanceOf(Exception.class);
    }
}
