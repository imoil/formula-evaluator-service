package com.imoil.formula.registry.repository;

import com.imoil.formula.registry.domain.SensorStatus;
import com.imoil.formula.registry.domain.SensorType;
import com.imoil.formula.registry.entity.SensorRegistryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SensorRegistryRepository extends JpaRepository<SensorRegistryEntity, Long> {

    Optional<SensorRegistryEntity> findBySerialNumber(Long serialNumber);

    Optional<SensorRegistryEntity> findBySensorCode(String sensorCode);

    List<SensorRegistryEntity> findBySensorType(SensorType sensorType);

    List<SensorRegistryEntity> findBySensorTypeAndStatus(SensorType sensorType, SensorStatus status);

    List<SensorRegistryEntity> findByDeviceId(String deviceId);

    boolean existsByDeviceIdAndDeviceSensorIndex(String deviceId, Integer deviceSensorIndex);

    boolean existsBySensorCode(String sensorCode);

    Optional<SensorRegistryEntity> findByDeviceIdAndDeviceSensorIndex(String deviceId, Integer deviceSensorIndex);

    @Query("SELECT DISTINCT s.deviceId FROM SensorRegistryEntity s WHERE s.deviceId IS NOT NULL ORDER BY s.deviceId")
    List<String> findDistinctDeviceIds();

    @Query("SELECT COALESCE(MAX(s.serialNumber), 10000) FROM SensorRegistryEntity s")
    Long findMaxSerialNumber();

    @Query("SELECT s FROM SensorRegistryEntity s WHERE " +
           "(:type IS NULL OR s.sensorType = :type) AND " +
           "(:deviceId IS NULL OR s.deviceId = :deviceId) AND " +
           "(:keyword IS NULL OR " +
           " LOWER(s.sensorName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(s.sensorCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(s.category) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(COALESCE(s.formulaExpression, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " STR(s.serialNumber) LIKE CONCAT('%', :keyword, '%')) " +
           "ORDER BY s.serialNumber ASC")
    List<SensorRegistryEntity> searchSensors(
            @Param("type") SensorType type,
            @Param("deviceId") String deviceId,
            @Param("keyword") String keyword
    );
}
