package com.imoil.formula.engine.functions;

import com.googlecode.aviator.runtime.type.AviatorLong;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
    void testGetName() {
        assertThat(function.getName()).isEqualTo("window_max");
    }

    @Test
    void testCallWithNormalData() {
        env.put("window_data", Arrays.asList(10.0, 20.0, 5.0, 15.0));
        AviatorObject arg = AviatorLong.valueOf(2); // window_max(2)

        AviatorObject result = function.call(env, arg);

        // Last 2 are [5.0, 15.0], max is 15.0
        assertThat(result.getValue(env)).isEqualTo(15.0);
    }

    @Test
    void testCallWithLargerSizeThanData() {
        env.put("window_data", Arrays.asList(10.0, 20.0, 5.0));
        AviatorObject arg = AviatorLong.valueOf(5); // window_max(5)

        AviatorObject result = function.call(env, arg);

        // All data [10.0, 20.0, 5.0], max is 20.0
        assertThat(result.getValue(env)).isEqualTo(20.0);
    }

    @Test
    void testCallWithEmptyData() {
        env.put("window_data", Collections.emptyList());
        AviatorObject arg = AviatorLong.valueOf(3);

        AviatorObject result = function.call(env, arg);

        assertThat(result.getValue(env)).isEqualTo(0.0);
    }

    @Test
    void testCallWithNullData() {
        env.put("window_data", null);
        AviatorObject arg = AviatorLong.valueOf(3);

        AviatorObject result = function.call(env, arg);

        assertThat(result.getValue(env)).isEqualTo(0.0);
    }

    @Test
    void testCallWithNegativeValues() {
        env.put("window_data", Arrays.asList(-10.0, -5.0, -20.0));
        AviatorObject arg = AviatorLong.valueOf(3);

        AviatorObject result = function.call(env, arg);

        assertThat(result.getValue(env)).isEqualTo(-5.0);
    }
}
