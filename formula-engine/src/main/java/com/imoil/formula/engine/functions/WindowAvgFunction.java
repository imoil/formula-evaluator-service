package com.imoil.formula.engine.functions;

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
    protected double compute(List<Double> subList) {
        double sum = 0;
        for (Double value : subList) {
            sum += value;
        }
        return sum / subList.size();
    }
}
