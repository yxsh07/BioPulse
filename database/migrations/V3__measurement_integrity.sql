ALTER TABLE measurements
  ADD COLUMN value_numeric DECIMAL(16,6) NULL,
  ADD COLUMN original_value DECIMAL(16,6) NULL,
  ADD COLUMN original_unit VARCHAR(30) NULL,
  ADD COLUMN conversion_method VARCHAR(100) NULL,
  ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
UPDATE measurements SET value_numeric=value,original_value=value,original_unit=unit;
ALTER TABLE measurements
  ADD CONSTRAINT fk_measurement_metric FOREIGN KEY(metric) REFERENCES measurement_types(metric_code),
  ADD INDEX idx_measurement_valid(admission_id,metric,quality,observed_at,id),
  ADD INDEX idx_measurement_recording(recording_id,observed_at,id),
  ADD CONSTRAINT chk_measurement_conversion CHECK (conversion_method IS NULL OR (original_unit IS NOT NULL AND original_value IS NOT NULL));
CREATE TRIGGER bi_measurement_value BEFORE INSERT ON measurements FOR EACH ROW SET NEW.value_numeric = COALESCE(NEW.value_numeric,CAST(NEW.value AS DECIMAL(16,6))), NEW.original_value = COALESCE(NEW.original_value,CAST(NEW.value AS DECIMAL(16,6))), NEW.original_unit = COALESCE(NEW.original_unit,NEW.unit);
-- Alias names make the V1 columns' semantic contract explicit without copying data.
CREATE VIEW measurement_observations AS SELECT id AS measurement_id, admission_id,device_id,metric AS metric_code,
 observed_at AS recorded_at,received_at,value_numeric,quality AS quality_flag,source_id,
 recording_id AS source_recording_id,event_id AS ingestion_event_id,unit AS canonical_unit,
 original_value,original_unit,conversion_method FROM measurements;
