package com.imoil.formula.engine.functions;

import com.googlecode.aviator.runtime.type.AviatorLong;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WindowAvgFunctionTest {

    private WindowAvgFunction function;
    private Map<String, Object> env;

    @BeforeEach
    void setUp() {
        function = new WindowAvgFunction();
        env = new HashMap<>();
    }

    @Test
    void testGetName() {
        assertThat(function.getName()).isEqualTo("window_avg");
    }

    @Test
    void testCallWithNullWindowData() {
        env.put("window_data", null);
        AviatorObject result = function.call(env, AviatorLong.valueOf(3));
        assertThat(result.getValue(env)).isEqualTo(0.0);
    }

    @Test
    void testCallWithEmptyWindowData() {
        env.put("window_data", List.of());
        AviatorObject result = function.call(env, AviatorLong.valueOf(3));
        assertThat(result.getValue(env)).isEqualTo(0.0);
    }

    @Test
    void testCallWithDataSmallerThanSize() {
        env.put("window_data", Arrays.asList(10.0, 20.0));
        // size 5, data size 2 -> should average all 2: (10 + 20) / 2 = 15.0
        AviatorObject result = function.call(env, AviatorLong.valueOf(5));
        assertThat(result.getValue(env)).isEqualTo(15.0);
    }

    @Test
    void testCallWithDataLargerThanSize() {
        env.put("window_data", Arrays.asList(10.0, 20.0, 30.0, 40.0, 50.0));
        // size 3, data size 5 -> should average last 3 [30.0, 40.0, 50.0]: (30+40+50)/3 = 40.0
        AviatorObject result = function.call(env, AviatorLong.valueOf(3));
        assertThat(result.getValue(env)).isEqualTo(40.0);
    }

    @Test
    void testCallWithDataEqualToSize() {
        env.put("window_data", Arrays.asList(10.0, 20.0, 30.0));
        // size 3, data size 3 -> should average all 3: (10+20+30)/3 = 20.0
        AviatorObject result = function.call(env, AviatorLong.valueOf(3));
        assertThat(result.getValue(env)).isEqualTo(20.0);
    }

    @Test
    void testCallWithZeroOrNegativeSize() {
        env.put("window_data", Arrays.asList(10.0, 20.0, 30.0));
        // when size is 0, sublist should be empty or handled safely by AbstractWindowFunction logic
        AviatorObject resultZero = function.call(env, AviatorLong.valueOf(0));
        assertThat(resultZero.getValue(env)).isNotNull();

        AviatorObject resultNegative = function.call(env, AviatorLong.valueOf(-5));
        assertThat(resultNegative.getValue(env)).isNotNull();
    }
}
