-- Optional, fictional registry seed. Do not use as real clinical data.
-- Run only on a clean development database. The application's DEMO_PASSWORD seed is separate.
INSERT INTO wards(name) VALUES ('Synthetic Demo Ward A'),('Synthetic Demo Ward B');
INSERT INTO beds(ward_id,label)
SELECT w.id,CONCAT('Demo Bed ',n.n) FROM wards w
JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) n
WHERE w.name IN ('Synthetic Demo Ward A','Synthetic Demo Ward B');
INSERT INTO users(username,password_hash,role,enabled)
VALUES ('demo-disabled-doctor@example.invalid','!DISABLED_SYNTHETIC_FIXTURE!','DOCTOR',FALSE),
       ('demo-disabled-nurse@example.invalid','!DISABLED_SYNTHETIC_FIXTURE!','NURSE',FALSE);
INSERT INTO patients(code,display_name,date_of_birth) VALUES
('SYN-001','Synthetic Patient 01',NULL),('SYN-002','Synthetic Patient 02',NULL),
('SYN-003','Synthetic Patient 03',NULL),('SYN-004','Synthetic Patient 04',NULL),
('SYN-005','Synthetic Patient 05',NULL),('SYN-006','Synthetic Patient 06',NULL),
('SYN-007','Synthetic Patient 07',NULL),('SYN-008','Synthetic Patient 08',NULL),
('SYN-009','Synthetic Patient 09',NULL),('SYN-010','Synthetic Patient 10',NULL);
INSERT INTO devices(serial,adapter_type,online) VALUES
('SYN-ECG-001','ECG',FALSE),('SYN-PPG-001','PPG',FALSE),('SYN-RESP-001','RESP',FALSE),
('SYN-BP-001','BP',FALSE),('SYN-TEMP-001','TEMP',FALSE);
INSERT INTO audit_logs(actor,action,entity_type,entity_id)
VALUES ('synthetic_demo_seed','CREATE_SYNTHETIC_FIXTURE','seed','synthetic_demo.sql');
