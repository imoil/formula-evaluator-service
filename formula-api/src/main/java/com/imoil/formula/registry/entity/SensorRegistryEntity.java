package com.imoil.formula.registry.entity;

import com.imoil.formula.registry.domain.SensorStatus;
import com.imoil.formula.registry.domain.SensorType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Oracle 19c SENSOR_REGISTRY 테이블 매핑 엔티티.
 * IoT 물리 장치 센서 및 수식 센서를 시퀀스 기반 단일 일련번호(serialNumber) 체계로 통합 관리.
 */
@Entity
@Table(name = "SENSOR_REGISTRY")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorRegistryEntity {

    /**
     * 전역 고유 일련번호 (PK)
     * Oracle 시퀀스 SENSOR_SERIAL_SEQ (10001부터 시작)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sensor_serial_seq_gen")
    @SequenceGenerator(
            name = "sensor_serial_seq_gen",
            sequenceName = "SENSOR_SERIAL_SEQ",
            initialValue = 10001,
            allocationSize = 1
    )
    @Column(name = "SERIAL_NUMBER", nullable = false)
    private Long serialNumber;

    /** 고유 식별 코드 (예: PHY-DEV001-S000, FML-PUMP-EFF) */
    @Column(name = "SENSOR_CODE", length = 64, nullable = false, unique = true)
    private String sensorCode;

    /** 센서 이름 */
    @Column(name = "SENSOR_NAME", length = 128, nullable = false)
    private String sensorName;

    /** 센서 구분 (PHYSICAL / FORMULA) */
    @Enumerated(EnumType.STRING)
    @Column(name = "SENSOR_TYPE", length = 20, nullable = false)
    private SensorType sensorType;

    /** 카테고리 (Temperature, Pressure, Flow, Vibration, Efficiency 등) */
    @Column(name = "CATEGORY", length = 64)
    private String category;

    /** 단위 (°C, bar, kW, %, m3/h 등) */
    @Column(name = "UNIT", length = 32)
    private String unit;

    /** IoT 장치 식별자 (물리 센서의 경우 필수, 예: DEVICE-001, TURBINE-01) */
    @Column(name = "DEVICE_ID", length = 64)
    private String deviceId;

    /** 장치 로컬 센서 인덱스 (0 ~ 999 등) */
    @Column(name = "DEVICE_SENSOR_INDEX")
    private Integer deviceSensorIndex;

    /** 수식 센서 평가식 (수식 센서의 경우 필수, 예: 'SN_10001 * 0.8 + SN_10002 * 0.2') */
    @Column(name = "FORMULA_EXPRESSION", length = 1000)
    private String formulaExpression;

    /** 센서 설명 */
    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /** 센서 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    @Builder.Default
    private SensorStatus status = SensorStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;
}
