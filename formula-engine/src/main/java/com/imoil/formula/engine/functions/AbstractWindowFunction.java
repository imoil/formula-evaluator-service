package com.imoil.formula.engine.functions;

import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorDouble;
import com.googlecode.aviator.runtime.type.AviatorObject;

import java.util.List;
import java.util.Map;

/**
 * 윈도우 함수의 공통 로직을 처리하는 추상 클래스.
 * 슬라이딩 윈도우 히스토리 컨텍스트 바인딩 불변식을 내부로 캡슐화합니다.
 */
public abstract class AbstractWindowFunction extends AbstractFunction {

    private static final String ENV_KEY_WINDOW_DATA = "window_data";

    public static void bindWindowHistory(Map<String, Object> env, List<Double> windowHistory) {
        if (env != null && windowHistory != null) {
            env.put(ENV_KEY_WINDOW_DATA, windowHistory);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Double> extractWindowHistory(Map<String, Object> env) {
        if (env == null) {
            return null;
        }
        return (List<Double>) env.get(ENV_KEY_WINDOW_DATA);
    }

    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
        // 인자(size)를 추출
        int size = ((Number) arg1.getValue(env)).intValue();

        List<Double> windowData = extractWindowHistory(env);

        if (windowData == null || windowData.isEmpty()) {
            return AviatorDouble.valueOf(0.0);
        }

        // 최근 size 만큼의 데이터 추출 (음수 size 등에 대한 방어 로직 포함)
        int elements = Math.min(size, windowData.size());
        int startIndex = Math.max(0, Math.min(windowData.size(), windowData.size() - elements));
        List<Double> subList = windowData.subList(startIndex, windowData.size());

        return AviatorDouble.valueOf(compute(subList));
    }

    /**
     * 구체적인 계산 로직을 구현합니다.
     * @param subList 최근 데이터 리스트
     * @return 계산 결과값
     */
    protected abstract double compute(List<Double> subList);
}
