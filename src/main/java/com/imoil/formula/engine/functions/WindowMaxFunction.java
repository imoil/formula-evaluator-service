package com.imoil.formula.engine.functions;

import com.googlecode.aviator.runtime.type.AviatorDouble;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 커스텀 윈도우 함수: window_max(size)
 */
@Component
public class WindowMaxFunction extends AbstractWindowFunction {
    
    @Override
    public String getName() {
        return "window_max";
    }

    @Override
    protected AviatorObject compute(List<Double> windowData, int startIndex, int elements) {
        double max = -Double.MAX_VALUE;
        for (int i = startIndex; i < windowData.size(); i++) {
            if (windowData.get(i) > max) {
                max = windowData.get(i);
            }
        }
        
        return AviatorDouble.valueOf(max);
    }
}
