-- Additive simulator scenario metadata. Existing sessions remain NORMAL.
ALTER TABLE simulation_sessions
  ADD COLUMN scenario_mode VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
  ADD COLUMN scenario_from_seconds DOUBLE NULL,
  ADD COLUMN scenario_to_seconds DOUBLE NULL,
  ADD COLUMN scenario_magnitude DOUBLE NULL,
  ADD CONSTRAINT chk_simulation_scenario CHECK (
    scenario_mode IN ('NORMAL','ABNORMAL_DEMO','SENSOR_OFFLINE','MISSING_DATA','NOISY_SIGNAL','DELAYED_TELEMETRY')
    AND (
      (scenario_mode='NORMAL'
        AND scenario_from_seconds IS NULL
        AND scenario_to_seconds IS NULL
        AND scenario_magnitude IS NULL)
      OR
      (scenario_mode<>'NORMAL'
        AND scenario_from_seconds IS NOT NULL
        AND scenario_to_seconds IS NOT NULL
        AND scenario_magnitude IS NOT NULL
        AND scenario_from_seconds >= 0
        AND scenario_to_seconds > scenario_from_seconds
        AND scenario_magnitude >= 0
        AND scenario_magnitude <= 100)
    )
  ),
  ADD INDEX idx_simulation_created(created_at,id);
