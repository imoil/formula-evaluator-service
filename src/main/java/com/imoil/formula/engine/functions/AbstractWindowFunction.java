package com.imoil.formula.engine.functions;

import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorDouble;
import com.googlecode.aviator.runtime.type.AviatorObject;

import java.util.List;
import java.util.Map;

/**
 * 윈도우 함수의 공통 로직을 처리하는 추상 클래스
 */
public abstract class AbstractWindowFunction extends AbstractFunction {

    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
        // 인자(size)를 추출
        int size = ((Number) arg1.getValue(env)).intValue();

        @SuppressWarnings("unchecked")
        List<Double> windowData = (List<Double>) env.get("window_data");

        if (windowData == null || windowData.isEmpty()) {
            return AviatorDouble.valueOf(0.0);
        }

        int elements = Math.min(size, windowData.size());
        int startIndex = windowData.size() - elements;

        return compute(windowData, startIndex, elements);
    }

    protected abstract AviatorObject compute(List<Double> windowData, int startIndex, int elements);
}
