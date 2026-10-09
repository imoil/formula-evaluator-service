package com.imoil.formula.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dynamic Rule Meta Data Model
 * Represents the rules required for the stream parsing flow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DynamicRule {
    private String ruleId;
    
    // 규칙을 적용할 대상 센서 ID
    private String targetSensorId;
    
    // AviatorScript 엔진에 의해 수행될 수식 구문 
    // e.g. "value * 1.5 + window_avg(5)"
    private String expression;
}
