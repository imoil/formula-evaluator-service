package com.imoil.formula.engine.functions;

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
    protected double compute(List<Double> subList) {
        double max = -Double.MAX_VALUE;
        for (Double value : subList) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }
}
