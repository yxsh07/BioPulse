ALTER TABLE reports MODIFY content LONGTEXT NULL,
  ADD COLUMN owner_user_id BIGINT NULL,
  ADD COLUMN media_type VARCHAR(100) NOT NULL DEFAULT 'text/csv',
  ADD COLUMN file_uri VARCHAR(1000) NULL,
  ADD COLUMN binary_content LONGBLOB NULL,
  ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  ADD CONSTRAINT fk_report_owner FOREIGN KEY(owner_user_id) REFERENCES users(id),
  ADD INDEX idx_report_owner(owner_user_id,created_at,id),
  ADD CONSTRAINT chk_report_window CHECK (to_at > from_at),
  ADD CONSTRAINT chk_report_content CHECK ((content IS NOT NULL) + (file_uri IS NOT NULL) + (binary_content IS NOT NULL) = 1);
UPDATE reports r JOIN users u ON u.username=r.created_by SET r.owner_user_id=u.id WHERE r.owner_user_id IS NULL;
CREATE TRIGGER bi_report_owner BEFORE INSERT ON reports FOR EACH ROW SET NEW.owner_user_id = COALESCE(NEW.owner_user_id,(SELECT id FROM users WHERE username=NEW.created_by));
CREATE TABLE audit_logs (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  actor_user_id BIGINT NULL,
  actor VARCHAR(100) NOT NULL,
  action VARCHAR(100) NOT NULL,
  entity_type VARCHAR(100) NOT NULL,
  entity_id VARCHAR(200) NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  detail JSON NULL,
  CONSTRAINT fk_audit_actor FOREIGN KEY(actor_user_id) REFERENCES users(id),
  INDEX idx_audit_actor(actor,occurred_at,id),
  INDEX idx_audit_entity(entity_type,entity_id,occurred_at,id)
) ENGINE=InnoDB;
INSERT INTO audit_logs(actor,action,entity_type,entity_id,occurred_at)
 SELECT actor,action,'legacy_resource',resource,occurred_at FROM audit_events;
CREATE TRIGGER ai_audit_event AFTER INSERT ON audit_events FOR EACH ROW
  INSERT INTO audit_logs(actor,action,entity_type,entity_id,occurred_at)
  VALUES (NEW.actor,NEW.action,SUBSTRING_INDEX(NEW.resource,':',1),
    IF(LOCATE(':',NEW.resource)>0,SUBSTRING(NEW.resource,LOCATE(':',NEW.resource)+1),''),NEW.occurred_at);
