ALTER TABLE data_sources
  ADD COLUMN publisher VARCHAR(200) NULL,
  ADD COLUMN dataset_name VARCHAR(200) NULL,
  ADD COLUMN dataset_version VARCHAR(100) NULL,
  ADD COLUMN license_name VARCHAR(150) NULL,
  ADD COLUMN attribution TEXT NULL,
  ADD COLUMN original_url VARCHAR(1000) NULL,
  ADD COLUMN provenance_class VARCHAR(40) NOT NULL DEFAULT 'LEGACY_UNCLASSIFIED',
  ADD COLUMN imported_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD CONSTRAINT chk_provenance_class CHECK (provenance_class IN ('ORIGINAL_RESEARCH','SYNTHETIC_CLINICAL','DEMONSTRATION_ASSOCIATION','MANUAL_SIMULATION','LEGACY_UNCLASSIFIED'));
UPDATE data_sources SET provenance_class='SYNTHETIC_CLINICAL' WHERE name='Framer research fixture';
ALTER TABLE source_recordings
  ADD COLUMN external_recording_id VARCHAR(200) NULL,
  ADD COLUMN original_subject_ref VARCHAR(200) NULL,
  ADD COLUMN original_recording_ref VARCHAR(200) NULL,
  ADD COLUMN available_signals JSON NULL,
  ADD COLUMN sample_rate_hz DECIMAL(12,4) NULL,
  ADD COLUMN duration_seconds DECIMAL(18,6) NULL,
  ADD COLUMN imported_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
UPDATE source_recordings SET external_recording_id=CONCAT('legacy:',id),duration_seconds=TIMESTAMPDIFF(MICROSECOND,start_at,end_at)/1000000;
ALTER TABLE source_recordings MODIFY external_recording_id VARCHAR(200) NOT NULL DEFAULT '',
  ADD CONSTRAINT uq_external_recording UNIQUE(external_recording_id),
  ADD INDEX idx_replay_source(source_id,start_at,id),
  ADD CONSTRAINT chk_recording_rate CHECK (sample_rate_hz IS NULL OR sample_rate_hz > 0),
  ADD CONSTRAINT chk_recording_duration CHECK (duration_seconds IS NULL OR duration_seconds > 0);
CREATE TRIGGER bi_source_recording_external BEFORE INSERT ON source_recordings FOR EACH ROW SET NEW.external_recording_id = COALESCE(NULLIF(NEW.external_recording_id,''),UUID());
ALTER TABLE simulation_sessions ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD CONSTRAINT fk_simulation_output FOREIGN KEY(output_recording_id) REFERENCES source_recordings(id),
  ADD INDEX idx_simulation_state(state,updated_at,id),
  ADD CONSTRAINT chk_simulation_speed CHECK (speed > 0);
ALTER TABLE alert_rules ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  ADD CONSTRAINT fk_alert_rule_metric FOREIGN KEY(metric) REFERENCES measurement_types(metric_code),
  ADD INDEX idx_rule_admission_enabled(admission_id,enabled,metric),
  ADD CONSTRAINT chk_rule_gap CHECK (persistence_seconds >= 0 AND max_gap_seconds > 0);
ALTER TABLE alerts ADD COLUMN acknowledged_at TIMESTAMP(6) NULL,
  ADD COLUMN acknowledged_by BIGINT NULL,
  ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  ADD CONSTRAINT fk_alert_ack_user FOREIGN KEY(acknowledged_by) REFERENCES users(id),
  ADD INDEX idx_alert_active(state,severity,triggered_at,id);
ALTER TABLE alert_events ADD COLUMN actor_user_id BIGINT NULL,
  ADD CONSTRAINT fk_alert_event_user FOREIGN KEY(actor_user_id) REFERENCES users(id),
  ADD INDEX idx_alert_events_history(alert_id,occurred_at,id);
ALTER TABLE analytics_snapshots ADD COLUMN window_start_at TIMESTAMP(6) NULL,
  ADD COLUMN window_end_at TIMESTAMP(6) NULL,
  ADD COLUMN algorithm_version VARCHAR(100) NULL,
  ADD COLUMN source_coverage_start_at TIMESTAMP(6) NULL,
  ADD COLUMN source_coverage_end_at TIMESTAMP(6) NULL,
  ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD INDEX idx_analytics_window(admission_id,window_start_at,window_end_at),
  ADD CONSTRAINT chk_analytics_window CHECK (window_start_at IS NULL OR window_end_at > window_start_at);
