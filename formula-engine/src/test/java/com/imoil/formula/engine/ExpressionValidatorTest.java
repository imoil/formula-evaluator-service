package com.imoil.formula.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "value * 2.0 + 10",
            "value > 50 ? value * 1.1 : value",
            "window_avg(3) + window_max(5)",
            "(TEMP - 32) / 1.8 * 0.95",
            "A == 100 && B != 20",
            "value >= 10.5 || value <= 0.0"
    })
    @DisplayName("정상적인 수식 검증 통과 테스트")
    void testValidExpressions(String expression) {
        assertThatCode(() -> ExpressionValidator.validate(expression))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null 또는 빈 수식 입력 시 IllegalArgumentException 발생")
    void testNullOrBlankExpressions() {
        assertThatThrownBy(() -> ExpressionValidator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Expression text must not be null or empty.");

        assertThatThrownBy(() -> ExpressionValidator.validate(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Expression text must not be null or empty.");

        assertThatThrownBy(() -> ExpressionValidator.validate("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Expression text must not be null or empty.");
    }

    @Test
    @DisplayName("최대 길이(500자) 초과 수식 입력 시 IllegalArgumentException 발생")
    void testExceedsMaxLength() {
        String longExpr = "value + ".repeat(100) + "1.0";
        assertThatThrownBy(() -> ExpressionValidator.validate(longExpr))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum allowed length");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "value * 2; System.exit(0)",
            "let x = { value: 10 }",
            "arr[0] + 1",
            "exec('ls')",
            "\"malicious_string\"",
            "`command`"
    })
    @DisplayName("허용되지 않은 특수문자(; { } [ ] ' \" `) 포함 시 IllegalArgumentException 발생")
    void testInvalidCharacters(String expression) {
        assertThatThrownBy(() -> ExpressionValidator.validate(expression))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a = 10",
            "x = y + 1",
            "val = 5.0"
    })
    @DisplayName("단독 대입 연산자(=) 포함 시 IllegalArgumentException 발생")
    void testAssignmentOperator(String expression) {
        assertThatThrownBy(() -> ExpressionValidator.validate(expression))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Assignment operations are not allowed in expressions.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "let a = 10",
            "fn(x) return x",
            "while(true) 1",
            "for(x in list) x",
            "exec(cmd)",
            "eval(code)",
            "sys.date()",
            "new ProcessBuilder()",
            "java.lang.System"
    })
    @DisplayName("금지된 키워드/함수 포함 시 IllegalArgumentException 발생")
    void testForbiddenKeywords(String expression) {
        assertThatThrownBy(() -> ExpressionValidator.validate(expression))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
