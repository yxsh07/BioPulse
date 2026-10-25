package org.biopulse.jdbc;

import java.time.*;
import java.util.*;
import org.biopulse.dao.RegistryDao;
import org.biopulse.domain.*;
import org.biopulse.dto.Requests.*;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRegistryDao implements RegistryDao {
  private final Sql sql;

  public JdbcRegistryDao(Sql sql) {
    this.sql = sql;
  }

  private final Sql.Mapper<Patient> pm =
      r ->
          new Patient(
              r.getLong("id"),
              r.getString("code"),
              r.getString("display_name"),
              r.getDate("date_of_birth") == null ? null : r.getDate("date_of_birth").toLocalDate());
  private final Sql.Mapper<Admission> am =
      r ->
          new Admission(
              r.getLong("id"),
              r.getLong("patient_id"),
              r.getLong("bed_id"),
              AdmissionStatus.valueOf(r.getString("status")),
              Sql.instant(r, "admitted_at"),
              Sql.instant(r, "discharged_at"));
  private final Sql.Mapper<MedicalDevice> dm =
      r ->
          new MedicalDevice(
              r.getLong("id"),
              r.getString("serial"),
              r.getString("adapter_type"),
              r.getBoolean("online"));
  private final Sql.Mapper<DataSource> sm =
      r ->
          new DataSource(
              r.getLong("id"),
              r.getString("name"),
              SourceType.valueOf(r.getString("type")),
              r.getString("provenance"));
  private final Sql.Mapper<SourceRecording> rm =
      r ->
          new SourceRecording(
              r.getLong("id"),
              r.getLong("source_id"),
              r.getString("name"),
              Sql.instant(r, "start_at"),
              Sql.instant(r, "end_at"));

  public List<Patient> patients(int offset, int limit) {
    return sql.query("SELECT * FROM patients ORDER BY id LIMIT ? OFFSET ?", pm, limit, offset);
  }

  public Optional<Patient> patient(long id) {
    return sql.query("SELECT * FROM patients WHERE id=?", pm, id).stream().findFirst();
  }

  public Patient createPatient(PatientCreate r) {
    long id =
        sql.insert(
            "INSERT INTO patients(code,display_name,date_of_birth) VALUES(?,?,?)",
            r.code(),
            r.displayName(),
            r.dateOfBirth());
    return patient(id).orElseThrow();
  }

  public List<Admission> admissions(int offset, int limit) {
    return sql.query("SELECT * FROM admissions ORDER BY id LIMIT ? OFFSET ?", am, limit, offset);
  }

  public Optional<Admission> admission(long id) {
    return sql.query("SELECT * FROM admissions WHERE id=?", am, id).stream().findFirst();
  }

  public Admission createAdmission(AdmissionCreate r) {
    long id =
        sql.insert(
            "INSERT INTO admissions(patient_id,bed_id,status,admitted_at) VALUES(?,?,'ACTIVE',?)",
            r.patientId(),
            r.bedId(),
            Instant.now());
    return admission(id).orElseThrow();
  }

  public List<MedicalDevice> devices() {
    return sql.query("SELECT * FROM devices ORDER BY id", dm);
  }

  public Optional<MedicalDevice> device(long id) {
    return sql.query("SELECT * FROM devices WHERE id=?", dm, id).stream().findFirst();
  }

  public MedicalDevice createDevice(DeviceCreate r) {
    long id =
        sql.insert(
            "INSERT INTO devices(serial,adapter_type) VALUES(?,?)", r.serial(), r.adapterType());
    return device(id).orElseThrow();
  }

  public DeviceAssignment assign(AssignmentCreate r) {
    Instant now = Instant.now();
    long id =
        sql.insert(
            "INSERT INTO device_assignments(device_id,admission_id,assigned_at) VALUES(?,?,?)",
            r.deviceId(),
            r.admissionId(),
            now);
    return new DeviceAssignment(id, r.deviceId(), r.admissionId(), now, null);
  }

  public boolean assigned(long admission, long device, Instant at) {
    return !sql.query(
            "SELECT id FROM device_assignments WHERE admission_id=? AND device_id=? AND"
                + " assigned_at<=? AND (ended_at IS NULL OR ended_at>?)",
            r -> r.getLong(1),
            admission,
            device,
            at,
            at)
        .isEmpty();
  }

  public Optional<DataSource> source(long id) {
    return sql.query("SELECT * FROM data_sources WHERE id=?", sm, id).stream().findFirst();
  }

  public List<DataSource> sources() {
    return sql.query("SELECT * FROM data_sources ORDER BY id", sm);
  }

  public DataSource createSource(SourceCreate r) {
    long id =
        sql.insert(
            "INSERT INTO data_sources(name,type,provenance) VALUES(?,?,?)",
            r.name(),
            r.type(),
            r.provenance());
    return source(id).orElseThrow();
  }

  public Optional<SourceRecording> recording(long id) {
    return sql.query("SELECT * FROM source_recordings WHERE id=?", rm, id).stream().findFirst();
  }

  public List<SourceRecording> recordings() {
    return sql.query("SELECT * FROM source_recordings ORDER BY id", rm);
  }

  public SourceRecording createRecording(RecordingCreate r) {
    long id =
        sql.insert(
            "INSERT INTO source_recordings(source_id,name,start_at,end_at) VALUES(?,?,?,?)",
            r.sourceId(),
            r.name(),
            r.startAt(),
            r.endAt());
    return recording(id).orElseThrow();
  }

  public List<Ward> wards() {
    return sql.query(
        "SELECT * FROM wards ORDER BY id", r -> new Ward(r.getLong("id"), r.getString("name")));
  }

  public List<Bed> beds() {
    return sql.query(
        "SELECT * FROM beds ORDER BY id",
        r -> new Bed(r.getLong("id"), r.getLong("ward_id"), r.getString("label")));
  }

  public void setOnline(long id, boolean online) {
    sql.update("UPDATE devices SET online=? WHERE id=?", online, id);
  }

  public List<Long> assignedAdmissions(long id) {
    return sql.query(
        "SELECT admission_id FROM device_assignments WHERE device_id=? AND ended_at IS NULL ORDER"
            + " BY admission_id",
        r -> r.getLong(1),
        id);
  }

  public void lockAdmission(long id) {
    sql.query("SELECT id FROM admissions WHERE id=? FOR UPDATE", r -> r.getLong(1), id);
  }
}
