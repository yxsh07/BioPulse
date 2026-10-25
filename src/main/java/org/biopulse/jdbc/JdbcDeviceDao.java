package org.biopulse.jdbc;

import java.util.List;
import java.util.Optional;
import org.biopulse.dao.DeviceDao;
import org.biopulse.domain.DeviceAssignment;
import org.biopulse.domain.MedicalDevice;
import org.biopulse.dto.Requests.AssignmentCreate;
import org.biopulse.dto.Requests.DeviceCreate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcDeviceDao implements DeviceDao {
  private final JdbcRegistryDao registry;
  private final Sql sql;

  public JdbcDeviceDao(JdbcRegistryDao registry, Sql sql) {
    this.registry = registry;
    this.sql = sql;
  }

  public MedicalDevice create(DeviceCreate device) {
    return registry.createDevice(device);
  }

  public Optional<MedicalDevice> find(long id) {
    return registry.device(id);
  }

  public List<MedicalDevice> all() {
    return registry.devices();
  }

  private final Sql.Mapper<MedicalDevice> mapper =
      r ->
          new MedicalDevice(
              r.getLong("id"),
              r.getString("serial"),
              r.getString("adapter_type"),
              r.getBoolean("online"));

  public List<MedicalDevice> allAuthorized(String actor, boolean admin) {
    return sql.query(
        "SELECT d.* FROM devices d WHERE ? OR EXISTS (SELECT 1 FROM device_assignments da JOIN"
            + " admissions a ON a.id=da.admission_id JOIN beds b ON b.id=a.bed_id JOIN user_wards uw"
            + " ON uw.ward_id=b.ward_id JOIN users u ON u.id=uw.user_id AND u.username=?"
            + " AND u.enabled=TRUE WHERE da.device_id=d.id) ORDER BY d.id",
        mapper,
        admin,
        actor);
  }

  @Transactional
  public DeviceAssignment assign(AssignmentCreate assignment) {
    // Lock the device, then let the generated active_device uniqueness guard the insert.
    if (sql.query(
            "SELECT id FROM devices WHERE id=? FOR UPDATE",
            r -> r.getLong(1),
            assignment.deviceId())
        .isEmpty()) throw new IllegalArgumentException("Unknown device");
    if (sql.query(
            "SELECT id FROM admissions WHERE id=? AND status='ACTIVE' FOR UPDATE",
            r -> r.getLong(1),
            assignment.admissionId())
        .isEmpty()) throw new IllegalArgumentException("Admission is not active");
    return registry.assign(assignment);
  }

  public List<DeviceAssignment> assignmentHistory(long deviceId) {
    return sql.query(
        "SELECT * FROM device_assignments WHERE device_id=? ORDER BY assigned_at DESC,id DESC",
        r ->
            new DeviceAssignment(
                r.getLong("id"),
                r.getLong("device_id"),
                r.getLong("admission_id"),
                Sql.instant(r, "assigned_at"),
                Sql.instant(r, "ended_at")),
        deviceId);
  }

  public void setOnline(long deviceId, boolean online) {
    registry.setOnline(deviceId, online);
  }

  public List<Long> assignedAdmissions(long deviceId) {
    return registry.assignedAdmissions(deviceId);
  }
}
