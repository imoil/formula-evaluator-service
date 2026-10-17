package com.imoil.formula.engine.functions;

import com.googlecode.aviator.runtime.type.AviatorDouble;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 커스텀 윈도우 함수: window_avg(size)
 */
@Component
public class WindowAvgFunction extends AbstractWindowFunction {
    
    @Override
    public String getName() {
        return "window_avg";
    }

    @Override
    protected AviatorObject compute(List<Double> windowData, int startIndex, int elements) {
        // 최근 size 만큼의 데이터에 대해 평균 계산
        double sum = 0;
        for (int i = startIndex; i < windowData.size(); i++) {
            sum += windowData.get(i);
        }
        
        return AviatorDouble.valueOf(sum / elements);
    }
}
