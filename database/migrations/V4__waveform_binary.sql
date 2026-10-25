-- V1 JSON rows remain readable; new writes use the lossless binary fields.
ALTER TABLE waveform_chunks
  MODIFY samples JSON NULL,
  ADD COLUMN sample_count INT NULL,
  ADD COLUMN encoding VARCHAR(30) NULL,
  ADD COLUMN compressed_payload LONGBLOB NULL,
  ADD COLUMN checksum BINARY(32) NULL,
  ADD COLUMN sample_offset BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN chunk_end_at TIMESTAMP(6) NULL,
  ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD INDEX idx_waveform_recording(recording_id,observed_at,id),
  ADD CONSTRAINT chk_waveform_rate CHECK (sample_rate_hz > 0),
  ADD CONSTRAINT chk_waveform_payload CHECK ((samples IS NOT NULL) OR (compressed_payload IS NOT NULL AND sample_count > 0 AND encoding IS NOT NULL AND checksum IS NOT NULL)),
  ADD CONSTRAINT chk_waveform_offset CHECK (sample_offset >= 0);
CREATE INDEX idx_waveform_overlap ON waveform_chunks(admission_id,metric,chunk_end_at,observed_at);
CREATE VIEW binary_waveform_chunks AS SELECT id AS chunk_id,admission_id,device_id,metric AS metric_code,
 recording_id AS source_recording_id,observed_at AS chunk_start_at,sample_rate_hz,sample_count,
 encoding,compressed_payload,checksum,sample_offset FROM waveform_chunks WHERE compressed_payload IS NOT NULL;
