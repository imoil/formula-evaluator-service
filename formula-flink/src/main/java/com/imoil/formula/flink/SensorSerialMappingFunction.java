package com.imoil.formula.flink;

import com.imoil.formula.domain.SensorData;
import com.imoil.formula.domain.SensorSerialResolver;
import org.apache.flink.api.common.functions.MapFunction;

/**
 * 카프카 컨슈머와 Flink 스트림 처리 연산자 사이에 위치하여
 * 센서 식별자를 단일 일련번호(serialNumber) 체계로 보정/매핑하는 Flink MapFunction.
 */
public class SensorSerialMappingFunction implements MapFunction<SensorData, SensorData> {

    private static final long serialVersionUID = 1L;

    private final SensorSerialResolver resolver;

    public SensorSerialMappingFunction() {
        this(SensorSerialResolver.getInstance());
    }

    public SensorSerialMappingFunction(SensorSerialResolver resolver) {
        this.resolver = (resolver != null) ? resolver : SensorSerialResolver.getInstance();
    }

    @Override
    public SensorData map(SensorData value) throws Exception {
        if (value == null || value.getSensorId() == null) {
            return value;
        }
        value.setSensorId(resolver.resolveOrKeepSensorId(value.getSensorId()));
        return value;
    }
}
