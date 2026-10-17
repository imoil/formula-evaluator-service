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

class WindowMaxFunctionTest {

    private WindowMaxFunction function;
    private Map<String, Object> env;

    @BeforeEach
    void setUp() {
        function = new WindowMaxFunction();
        env = new HashMap<>();
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
        env.put("window_data", Arrays.asList(10.0, 30.0, 20.0));
        // size 5, data size 3 -> should find max of all 3: 30.0
        AviatorObject result = function.call(env, AviatorLong.valueOf(5));
        assertThat(result.getValue(env)).isEqualTo(30.0);
    }

    @Test
    void testCallWithDataLargerThanSize() {
        env.put("window_data", Arrays.asList(50.0, 10.0, 20.0, 30.0, 25.0));
        // size 3, data size 5 -> should find max of last 3 [20.0, 30.0, 25.0]: 30.0
        AviatorObject result = function.call(env, AviatorLong.valueOf(3));
        assertThat(result.getValue(env)).isEqualTo(30.0);
    }

    @Test
    void testCallWithDataEqualToSize() {
        env.put("window_data", Arrays.asList(10.0, 40.0, 30.0));
        // size 3, data size 3 -> should find max of all 3: 40.0
        AviatorObject result = function.call(env, AviatorLong.valueOf(3));
        assertThat(result.getValue(env)).isEqualTo(40.0);
    }
}
