package com.imoil.formula.engine;

import java.util.regex.Pattern;

/**
 * 수식 표현식에 대한 보안 검증 유틸리티 클래스입니다.
 * Code Injection 및 DoS 공격 방지를 위해 입력 문자열의 길이, 허용 문자,
 * 금지된 키워드 및 대입 연산자 유무를 엄격하게 검증합니다.
 */
public final class ExpressionValidator {

    private static final int MAX_EXPRESSION_LENGTH = 500;

    // 허용된 문자로만 구성되었는지 검증하는 패턴 (영문자, 숫자, 언더스코어, 공백, 기본 사칙/논리/삼항 연산자, 괄호, 콤마, 마침표)
    private static final Pattern ALLOWED_CHARACTERS_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\s\\+\\-\\*/\\%\\?\\:\\>\\<\\=\\!\\&\\|\\(\\)\\,\\.]+$");

    // 단독 대입 연산자(=) 검증 패턴 (==, !=, >=, <= 제외)
    private static final Pattern ASSIGNMENT_OPERATOR_PATTERN = Pattern.compile("(?<![=><!])=(?![=])");

    // 보안상 위험하거나 불필요한 키워드/함수명 검증 패턴
    private static final Pattern DISALLOWED_KEYWORDS_PATTERN = Pattern.compile(
            "(?i)\\b(let|fn|def|while|for|in|return|try|catch|throw|use|import|include|load|new|Class|System|Runtime|Process|ProcessBuilder|exec|eval|sys|system|java|javax|org|com|net|lambda|nil|null)\\b"
    );

    private ExpressionValidator() {
        // Utility class
    }

    /**
     * 수식 표현식의 안전성을 검증합니다.
     *
     * @param expressionText 검증할 수식 문자열
     * @throws IllegalArgumentException 수식이 null이거나 빈 문자열, 길이 초과, 허용되지 않는 구문/키워드가 포함된 경우
     */
    public static void validate(String expressionText) {
        if (expressionText == null || expressionText.trim().isEmpty()) {
            throw new IllegalArgumentException("Expression text must not be null or empty.");
        }

        if (expressionText.length() > MAX_EXPRESSION_LENGTH) {
            throw new IllegalArgumentException("Expression text exceeds maximum allowed length of " + MAX_EXPRESSION_LENGTH + " characters.");
        }

        if (!ALLOWED_CHARACTERS_PATTERN.matcher(expressionText).matches()) {
            throw new IllegalArgumentException("Expression contains invalid or unsafe characters.");
        }

        if (ASSIGNMENT_OPERATOR_PATTERN.matcher(expressionText).find()) {
            throw new IllegalArgumentException("Assignment operations are not allowed in expressions.");
        }

        if (DISALLOWED_KEYWORDS_PATTERN.matcher(expressionText).find()) {
            throw new IllegalArgumentException("Expression contains disallowed keywords.");
        }
    }
}
