package com.imoil.formula.engine.functions;

import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorDouble;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 커스텀 윈도우 함수: window_max(size)
 */
@Component
public class WindowMaxFunction extends AbstractFunction {
    
    @Override
    public String getName() {
        return "window_max";
    }

    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
        // 인자(size) 추출
        int size = ((Number) arg1.getValue(env)).intValue();
        
        @SuppressWarnings("unchecked")
        List<Double> windowData = (List<Double>) env.get("window_data");
        
        if (windowData == null || windowData.isEmpty()) {
            return AviatorDouble.valueOf(0.0);
        }
        
        int elements = Math.min(size, windowData.size());
        double max = -Double.MAX_VALUE;
        int startIndex = windowData.size() - elements;
        
        for (int i = startIndex; i < windowData.size(); i++) {
            if (windowData.get(i) > max) {
                max = windowData.get(i);
            }
        }
        
        return AviatorDouble.valueOf(max);
    }
}
