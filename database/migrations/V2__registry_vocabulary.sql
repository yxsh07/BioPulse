-- Additive migration: preserve the V1 API and every existing observation.
ALTER TABLE users ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
ALTER TABLE patients ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
ALTER TABLE wards ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
ALTER TABLE beds ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
ALTER TABLE admissions ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), ADD CONSTRAINT chk_admission_interval CHECK (discharged_at IS NULL OR discharged_at > admitted_at);
ALTER TABLE device_assignments ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), ADD CONSTRAINT chk_assignment_interval CHECK (ended_at IS NULL OR ended_at > assigned_at), ADD INDEX idx_assignment_history(device_id, assigned_at, id);
CREATE TABLE device_types (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(30) NOT NULL UNIQUE,
  description VARCHAR(200) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;
INSERT INTO device_types(code,description) SELECT DISTINCT adapter_type, CONCAT('Existing ',adapter_type,' adapter') FROM devices;
INSERT INTO device_types(code,description) VALUES ('ECG','Electrocardiogram'),('PPG','Photoplethysmogram'),('RESP','Respiration'),('BP','Blood pressure'),('TEMP','Temperature'),('MULTI','Multiparameter monitor'),('OXIMETER','Pulse oximeter'),('RESPIRATORY','Respiratory monitor'),('NIBP','Noninvasive blood pressure monitor'),('TEMPERATURE','Temperature probe') ON DUPLICATE KEY UPDATE description=device_types.description;
ALTER TABLE devices ADD COLUMN device_type_id BIGINT NULL, ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
UPDATE devices d JOIN device_types t ON t.code=d.adapter_type SET d.device_type_id=t.id;
ALTER TABLE devices MODIFY device_type_id BIGINT NOT NULL DEFAULT 0, ADD CONSTRAINT fk_device_type FOREIGN KEY(device_type_id) REFERENCES device_types(id);
CREATE TRIGGER bi_devices_type BEFORE INSERT ON devices FOR EACH ROW SET NEW.device_type_id = COALESCE(NULLIF(NEW.device_type_id,0),(SELECT id FROM device_types WHERE code=NEW.adapter_type));
CREATE TABLE measurement_types (
  metric_code VARCHAR(20) NOT NULL PRIMARY KEY,
  canonical_unit VARCHAR(30) NOT NULL,
  description VARCHAR(200) NOT NULL,
  minimum_value DECIMAL(16,6) NULL,
  maximum_value DECIMAL(16,6) NULL,
  CHECK (minimum_value IS NULL OR maximum_value IS NULL OR maximum_value > minimum_value)
) ENGINE=InnoDB;
INSERT INTO measurement_types VALUES
('HR','bpm','Heart rate',0,350),('PR','bpm','Pulse rate',0,350),('SPO2','%','Oxygen saturation',0,100),
('RR','breaths/min','Respiratory rate',0,150),('SBP','mmHg','Systolic blood pressure',0,400),
('DBP','mmHg','Diastolic blood pressure',0,300),('TEMP','Cel','Body temperature',20,50),('PULSE','bpm','Legacy pulse rate alias',0,350);
CREATE TABLE patient_clinical_context (
  admission_id BIGINT NOT NULL PRIMARY KEY,
  supplemental_oxygen BOOLEAN NULL,
  spo2_scale INT NULL,
  consciousness VARCHAR(40) NULL,
  notes TEXT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_patient_context_admission FOREIGN KEY(admission_id) REFERENCES admissions(id),
  CHECK (spo2_scale IS NULL OR spo2_scale IN (1,2))
) ENGINE=InnoDB;
INSERT INTO patient_clinical_context(admission_id,supplemental_oxygen,spo2_scale,consciousness,notes)
SELECT admission_id,supplemental_oxygen,spo2_scale,consciousness,notes FROM clinical_contexts;
