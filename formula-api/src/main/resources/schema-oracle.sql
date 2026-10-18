-- ====================================================================
-- Oracle 19c Schema Definition for Unified Sensor Registry
-- Provides unified sequence-based serial numbering across:
--   1) IoT Device Physical Sensors (formula-emulator multi-devices)
--   2) Calculated Formula Sensors (derived formulas)
-- Prevents sensor index collision across different IoT devices.
-- ====================================================================

-- 1. Sequence for Global Unique Sensor Serial Number (Starts at 10001)
CREATE SEQUENCE SENSOR_SERIAL_SEQ
    START WITH 10001
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- 2. Sensor Registry Table
CREATE TABLE SENSOR_REGISTRY (
    SERIAL_NUMBER          NUMBER(19) NOT NULL,
    SENSOR_CODE            VARCHAR2(64) NOT NULL,
    SENSOR_NAME            VARCHAR2(128) NOT NULL,
    SENSOR_TYPE            VARCHAR2(20) NOT NULL, -- 'PHYSICAL' or 'FORMULA'
    CATEGORY               VARCHAR2(64),
    UNIT                   VARCHAR2(32),
    DEVICE_ID              VARCHAR2(64),          -- IoT Device ID (e.g. 'DEVICE-001')
    DEVICE_SENSOR_INDEX    NUMBER(10),            -- Local sensorIndex within device (0 ~ 999)
    FORMULA_EXPRESSION     VARCHAR2(1000),        -- Formula Expression (e.g. 'P_10001 * 1.8 + 32')
    DESCRIPTION            VARCHAR2(500),
    STATUS                 VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
    CREATED_AT             TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UPDATED_AT             TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_SENSOR_REGISTRY PRIMARY KEY (SERIAL_NUMBER),
    CONSTRAINT UQ_SENSOR_CODE UNIQUE (SENSOR_CODE),
    CONSTRAINT CHK_SENSOR_TYPE CHECK (SENSOR_TYPE IN ('PHYSICAL', 'FORMULA'))
);

-- Unique index to prevent duplicate (DEVICE_ID, DEVICE_SENSOR_INDEX) mapping for physical sensors
CREATE UNIQUE INDEX UQ_DEVICE_SENSOR_IDX ON SENSOR_REGISTRY (
    CASE WHEN SENSOR_TYPE = 'PHYSICAL' THEN DEVICE_ID END,
    CASE WHEN SENSOR_TYPE = 'PHYSICAL' THEN DEVICE_SENSOR_INDEX END
);

-- Query optimization indexes
CREATE INDEX IDX_SENSOR_TYPE ON SENSOR_REGISTRY (SENSOR_TYPE);
CREATE INDEX IDX_DEVICE_ID ON SENSOR_REGISTRY (DEVICE_ID);
CREATE INDEX IDX_SENSOR_CAT ON SENSOR_REGISTRY (CATEGORY);

COMMENT ON TABLE SENSOR_REGISTRY IS 'Oracle 19c Global Sensor Registry & Collision-Free Serial Management';
COMMENT ON COLUMN SENSOR_REGISTRY.SERIAL_NUMBER IS 'Global Unique Serial Number (PK, generated via SENSOR_SERIAL_SEQ)';
COMMENT ON COLUMN SENSOR_REGISTRY.SENSOR_CODE IS 'Human-readable unique code (e.g. PHY-DEV001-S000, FML-TEMP-AVG)';
COMMENT ON COLUMN SENSOR_REGISTRY.SENSOR_TYPE IS 'PHYSICAL (IoT device sensor) or FORMULA (virtual calculated sensor)';
COMMENT ON COLUMN SENSOR_REGISTRY.DEVICE_ID IS 'Originating IoT Device ID from formula-emulator';
COMMENT ON COLUMN SENSOR_REGISTRY.DEVICE_SENSOR_INDEX IS 'Local device sensor index which can collide across devices without serial number';
COMMENT ON COLUMN SENSOR_REGISTRY.FORMULA_EXPRESSION IS 'Evaluation expression formula referencing other sensor serial numbers or codes';
