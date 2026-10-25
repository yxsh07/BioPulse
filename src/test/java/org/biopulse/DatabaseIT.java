package org.biopulse;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import org.biopulse.dao.MeasurementDao;
import org.biopulse.dao.ProvenanceDao;
import org.biopulse.dao.WaveformDao;
import org.biopulse.domain.MeasurementCode;
import org.biopulse.domain.QualityFlag;
import org.biopulse.domain.SourceType;
import org.biopulse.jdbc.Sql;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest(
    properties = {"biopulse.seed-demo=true", "biopulse.demo-password=IntegrationOnlyPassword!"})
class DatabaseIT {
  static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    String external = System.getenv("TEST_DB_URL");
    if (external == null || external.isBlank()) {
      mysql.start();
      registry.add("spring.datasource.url", mysql::getJdbcUrl);
      registry.add("spring.datasource.username", mysql::getUsername);
      registry.add("spring.datasource.password", mysql::getPassword);
    } else {
      registry.add("spring.datasource.url", () -> external);
      registry.add("spring.datasource.username", () -> System.getenv("TEST_DB_USER"));
      registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
    }
  }

  @Autowired Sql sql;
  @Autowired WaveformDao waveforms;
  @Autowired ProvenanceDao provenance;
  @Autowired MeasurementDao measurements;

  @Test
  @Transactional
  void schemaAndIntegrity() {
    assertEquals(
        21,
        sql.query(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND"
                    + " table_name IN"
                    + " ('users','patients','wards','beds','admissions','patient_clinical_context','device_types','devices','device_assignments','measurement_types','measurements','waveform_chunks','data_sources','source_recordings','simulation_sessions','alert_rules','alerts','alert_events','analytics_snapshots','reports','audit_logs')",
                r -> r.getInt(1))
            .getFirst());
    assertEquals(
        7,
        sql.query(
                "SELECT COUNT(*) FROM measurement_types WHERE metric_code IN"
                    + " ('HR','PR','SPO2','RR','SBP','DBP','TEMP')",
                r -> r.getInt(1))
            .getFirst());
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            sql.insert(
                "INSERT INTO admissions(patient_id,bed_id,status,admitted_at)"
                    + " VALUES(999999,1,'ACTIVE',?)",
                Instant.now()));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            sql.insert(
                "INSERT INTO device_assignments(device_id,admission_id,assigned_at) VALUES(1,2,?)",
                Instant.now()));
    String existingExternalId =
        sql.query(
                "SELECT external_recording_id FROM source_recordings LIMIT 1", r -> r.getString(1))
            .getFirst();
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            sql.insert(
                "INSERT INTO"
                    + " source_recordings(source_id,name,start_at,end_at,external_recording_id)"
                    + " VALUES(1,'duplicate',?,?,?)",
                Instant.now(),
                Instant.now().plusSeconds(1),
                existingExternalId));
  }

  @Test
  @Transactional
  void waveformStreamingRangeAndChecksumRollback() throws Exception {
    byte[] bytes =
        ByteBuffer.allocate(24)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putDouble(1.25)
            .putDouble(-0.0)
            .putDouble(2.5)
            .array();
    byte[] checksum = MessageDigest.getInstance("SHA-256").digest(bytes);
    Instant start = Instant.parse("2026-10-25T08:30:10Z");
    WaveformDao.Chunk chunk =
        new WaveformDao.Chunk(
            0,
            1,
            1,
            MeasurementCode.ECG,
            1L,
            start,
            start.plusMillis(120),
            25,
            3,
            250,
            "F64_LE",
            checksum);
    String event = "binary:" + UUID.randomUUID();
    long id =
        waveforms.insert(chunk, event, 1, "mV", new ByteArrayInputStream(bytes), bytes.length);
    ByteArrayOutputStream found = new ByteArrayOutputStream();
    waveforms.streamRange(
        1,
        MeasurementCode.ECG,
        start.plusMillis(50),
        start.plusMillis(200),
        (header, stream) -> {
          if (header.id() == id) stream.transferTo(found);
        });
    assertArrayEquals(bytes, found.toByteArray());
    assertThrows(
        org.springframework.dao.DuplicateKeyException.class,
        () ->
            waveforms.insert(chunk, event, 1, "mV", new ByteArrayInputStream(bytes), bytes.length));
    byte[] wrong = checksum.clone();
    wrong[0] ^= 1;
    WaveformDao.Chunk bad =
        new WaveformDao.Chunk(
            0,
            1,
            1,
            MeasurementCode.ECG,
            1L,
            start,
            start.plusMillis(120),
            25,
            3,
            250,
            "F64_LE",
            wrong);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            waveforms.insert(
                bad,
                "binary:" + UUID.randomUUID(),
                1,
                "mV",
                new ByteArrayInputStream(bytes),
                bytes.length));
    assertEquals(
        1,
        sql.query("SELECT COUNT(*) FROM waveform_chunks WHERE id=?", r -> r.getInt(1), id)
            .getFirst());
  }

  @Test
  void rangeIndexPlanAndSeedLabels() {
    assertEquals(
        "idx_measurement_history",
        sql.query(
                "EXPLAIN SELECT id FROM measurements WHERE admission_id=1 AND metric='HR' AND"
                    + " observed_at>='2026-10-25 08:30:00' AND observed_at<'2026-10-25 08:31:00'"
                    + " ORDER BY observed_at,id LIMIT 100",
                r -> r.getString("key"))
            .getFirst());
    assertTrue(
        java.util.Set.of("idx_waveform_history", "idx_waveform_overlap")
            .contains(
                sql.query(
                        "EXPLAIN SELECT id FROM waveform_chunks WHERE admission_id=1 AND"
                            + " metric='ECG' AND observed_at<'2026-10-25 08:31:00' AND"
                            + " chunk_end_at>'2026-10-25 08:30:00' ORDER BY observed_at,id LIMIT"
                            + " 100",
                        r -> r.getString("key"))
                    .getFirst()));
    assertTrue(
        sql
            .query("SELECT name FROM data_sources WHERE name LIKE '%fixture%'", r -> r.getString(1))
            .stream()
            .anyMatch(name -> name.toLowerCase().contains("fixture")));
  }

  @Test
  @Transactional
  void provenanceAndConvertedMeasurementRange() {
    long source =
        provenance.createSource(
            new ProvenanceDao.SourceInput(
                "Synthetic conversion test",
                SourceType.DATASET,
                "JUnit synthetic fixture",
                "SYNTHETIC_CLINICAL",
                "BioPulse Tests",
                "Fictional temperatures",
                "1",
                "TEST ONLY",
                "Synthetic fixture",
                null));
    Instant start = Instant.parse("2026-01-01T00:00:00Z");
    String external = "synthetic:" + UUID.randomUUID();
    long recording =
        provenance.createRecording(
            new ProvenanceDao.RecordingInput(
                source,
                external,
                "Synthetic recording",
                "subject-synthetic-1",
                "recording-synthetic-1",
                java.util.List.of("TEMP"),
                BigDecimal.ONE,
                start,
                start.plusSeconds(60)));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            provenance.createRecording(
                new ProvenanceDao.RecordingInput(
                    source,
                    external,
                    "duplicate",
                    null,
                    null,
                    java.util.List.of("TEMP"),
                    BigDecimal.ONE,
                    start,
                    start.plusSeconds(60))));
    long id =
        measurements.insertConverted(
            new MeasurementDao.ConvertedObservation(
                "converted:" + UUID.randomUUID(),
                1,
                1,
                MeasurementCode.TEMP,
                start.plusSeconds(10),
                start.plusSeconds(11),
                new BigDecimal("37.000000"),
                "Cel",
                new BigDecimal("98.600000"),
                "degF",
                "F_TO_C_V1",
                QualityFlag.GOOD,
                source,
                recording));
    assertEquals(
        "degF",
        sql.query("SELECT original_unit FROM measurements WHERE id=?", r -> r.getString(1), id)
            .getFirst());
    assertEquals(
        1,
        measurements.history(1, MeasurementCode.TEMP, start, start.plusSeconds(60), 100, 0).stream()
            .filter(m -> m.id() == id)
            .count());
    assertTrue(provenance.replayCatalog(10, 0).stream().noneMatch(r -> r.id() == recording));
  }
}
