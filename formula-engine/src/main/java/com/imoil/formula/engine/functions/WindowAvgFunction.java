package com.imoil.formula.engine.functions;

import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorDouble;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 커스텀 윈도우 함수: window_avg(size)
 */
@Component
public class WindowAvgFunction extends AbstractFunction {
    
    @Override
    public String getName() {
        return "window_avg";
    }

    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
        // 인자(size)를 추출
        int size = ((Number) arg1.getValue(env)).intValue();
        
        @SuppressWarnings("unchecked")
        List<Double> windowData = (List<Double>) env.get("window_data");
        
        if (windowData == null || windowData.isEmpty()) {
            return AviatorDouble.valueOf(0.0);
        }
        
        // 최근 size 만큼의 데이터에 대해 평균 계산
        int elements = Math.min(size, windowData.size());
        double sum = 0;
        int startIndex = windowData.size() - elements;
        
        for (int i = startIndex; i < windowData.size(); i++) {
            sum += windowData.get(i);
        }
        
        return AviatorDouble.valueOf(sum / elements);
    }
}
