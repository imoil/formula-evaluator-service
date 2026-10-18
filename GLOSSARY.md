# Formula Evaluator Service

Real-time streaming and on-demand evaluation context for high-frequency IoT sensor telemetry and dynamic user-defined formulas.

## Language

### Sensors & Identifiers

**Physical Sensor**:
An IoT hardware telemetry source attached to a device at a specific local sensor index (`deviceId` + `deviceSensorIndex`), assigned a globally unique Serial Number in the registry.
_Avoid_: Raw sensor, device channel

**Formula Sensor**:
A virtual sensor registered in the Sensor Registry whose time-series values are dynamically computed from a mathematical or windowed expression over Physical Sensors or other Formula Sensors.
_Avoid_: Derived metric, virtual channel, calculated tag

**Serial Number**:
The globally unique numeric identifier (`10001+`) assigned by the Sensor Registry to unify both Physical Sensors and Formula Sensors across streaming and on-demand paths without device-index collisions.
_Avoid_: Global ID, sequence ID

**Prefixed Serial Identifier**:
The `P_`-prefixed string representation of a Serial Number (e.g. `P_10001`, formerly `SN_10001` for legacy compatibility) used inside formula expressions so numeric serials are valid native identifier tokens (Parameter / Point).
_Avoid_: Prefixed ID, tag alias

**Device Sensor Key**:
The composite hardware address (`deviceId#sensorIndex`) identifying a Physical Sensor on an IoT device prior to Serial Number resolution.
_Avoid_: Hardware key, composite sensor ID

### Evaluation & Rules

**Dynamic Rule**:
A runtime evaluation specification broadcast to streaming workers that binds a target sensor's Serial Number to a formula expression, window-usage flag, and output Formula Sensor Serial Number (`derivedSensorId`).
_Avoid_: Streaming config, filter rule

**Window Function**:
A stateful sliding-window aggregation (`window_avg`, `window_max`, `window_min`) evaluated over the most recent $N$ telemetry values of a sensor stream.
_Avoid_: Rolling macro, history operator
