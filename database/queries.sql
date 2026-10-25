-- Bind every :parameter through PreparedStatement. Intervals are [from, to).
-- 1. Latest valid measurement of each metric for an admission.
SELECT m.* FROM measurements m JOIN (
  SELECT metric, MAX(observed_at) observed_at FROM measurements
  WHERE admission_id=:admission_id AND quality IN ('GOOD','FAIR') GROUP BY metric
) x ON x.metric=m.metric AND x.observed_at=m.observed_at
WHERE m.admission_id=:admission_id AND m.quality IN ('GOOD','FAIR')
  AND m.id=(SELECT MAX(m2.id) FROM measurements m2 WHERE m2.admission_id=m.admission_id
    AND m2.metric=m.metric AND m2.observed_at=m.observed_at AND m2.quality IN ('GOOD','FAIR'));

-- 2. Historical observations by metric and time range.
SELECT * FROM measurements WHERE admission_id=:admission_id AND metric=:metric
  AND observed_at>=:from_at AND observed_at<:to_at ORDER BY observed_at,id LIMIT :limit;

-- 3. Ward-wise active patient list.
SELECT w.id ward_id,b.label bed,p.id patient_id,p.code,p.display_name,a.id admission_id,a.admitted_at
FROM wards w JOIN beds b ON b.ward_id=w.id JOIN admissions a ON a.bed_id=b.id AND a.status='ACTIVE'
JOIN patients p ON p.id=a.patient_id WHERE w.id=:ward_id ORDER BY b.label,a.id;

-- 4. Device assignment history.
SELECT * FROM device_assignments WHERE device_id=:device_id ORDER BY assigned_at DESC,id DESC;

-- 5. Active alerts ordered by severity.
SELECT * FROM alerts WHERE state IN ('OPEN','ACKNOWLEDGED')
ORDER BY FIELD(severity,'CRITICAL','WARNING','INFO'),triggered_at,id LIMIT :limit;

-- 6. Alert count by period and severity.
SELECT severity,COUNT(*) alert_count FROM alerts WHERE triggered_at>=:from_at AND triggered_at<:to_at
GROUP BY severity;

-- 7. Min/max/average, valid readings only.
SELECT MIN(value_numeric) minimum,MAX(value_numeric) maximum,AVG(value_numeric) average
FROM measurements WHERE admission_id=:admission_id AND metric=:metric
AND observed_at>=:from_at AND observed_at<:to_at AND quality IN ('GOOD','FAIR');

-- 8. Missing/stale measurements; compare against an application-supplied cutoff.
SELECT t.metric_code,MAX(m.observed_at) latest_valid_at FROM measurement_types t
LEFT JOIN measurements m ON m.metric=t.metric_code AND m.admission_id=:admission_id
  AND m.quality IN ('GOOD','FAIR') AND m.observed_at>=:cutoff
GROUP BY t.metric_code HAVING latest_valid_at IS NULL;

-- 9. Patient monitoring report data.
SELECT p.code,p.display_name,a.id admission_id,m.metric,m.observed_at,m.received_at,
 m.value_numeric,m.unit,m.original_value,m.original_unit,m.quality,m.source_id,m.recording_id
FROM admissions a JOIN patients p ON p.id=a.patient_id
JOIN measurements m ON m.admission_id=a.id WHERE a.id=:admission_id
AND m.observed_at>=:from_at AND m.observed_at<:to_at ORDER BY m.observed_at,m.id;

-- 10. Dataset recordings available for replay.
SELECT r.*,s.publisher,s.dataset_name,s.dataset_version,s.license_name,s.attribution,s.original_url
FROM source_recordings r JOIN data_sources s ON s.id=r.source_id
WHERE s.provenance_class='ORIGINAL_RESEARCH' ORDER BY r.imported_at DESC,r.id DESC LIMIT :limit;

-- 11. Audit history by actor/entity. Use one of these selective forms.
SELECT * FROM audit_logs WHERE actor=:actor ORDER BY occurred_at DESC,id DESC LIMIT :limit;
SELECT * FROM audit_logs WHERE entity_type=:entity_type AND entity_id=:entity_id
ORDER BY occurred_at DESC,id DESC LIMIT :limit;

-- 12. Simulation session summary.
SELECT state,COUNT(*) session_count,MIN(created_at) first_created_at,MAX(updated_at) last_updated_at
FROM simulation_sessions GROUP BY state;

-- Query plan probes; verify a range access on idx_measurement_history and idx_waveform_history.
EXPLAIN SELECT id FROM measurements WHERE admission_id=1 AND metric='HR'
  AND observed_at>='2026-01-01' AND observed_at<'2026-01-02' ORDER BY observed_at,id LIMIT 100;
EXPLAIN SELECT id FROM waveform_chunks WHERE admission_id=1 AND metric='ECG'
  AND observed_at<'2026-01-02' AND chunk_end_at>'2026-01-01' ORDER BY observed_at,id LIMIT 100;
