package org.biopulse;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.biopulse.dao.*;
import org.biopulse.domain.*;
import org.biopulse.dto.Requests.*;
import org.biopulse.jdbc.Sql;
import org.biopulse.service.*;
import org.biopulse.simulation.bidmc.BidmcImportService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {"biopulse.seed-demo=true", "biopulse.demo-password=IntegrationOnlyPassword!"})
@AutoConfigureMockMvc
class ApplicationIT {
  static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry r) {
    String url = System.getenv("TEST_DB_URL");
    if (url == null || url.isBlank()) {
      mysql.start();
      r.add("spring.datasource.url", mysql::getJdbcUrl);
      r.add("spring.datasource.username", mysql::getUsername);
      r.add("spring.datasource.password", mysql::getPassword);
    } else {
      r.add("spring.datasource.url", () -> url);
      r.add("spring.datasource.username", () -> System.getenv("TEST_DB_USER"));
      r.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
    }
  }

  @AfterAll
  static void stopContainer() {
    if (mysql.isRunning()) mysql.stop();
  }

  @Autowired MockMvc mvc;
  @Autowired RegistryDao registry;
  @Autowired MeasurementDao measurements;
  @Autowired MeasurementIngestionService ingestion;
  @Autowired AlertService alerts;
  @Autowired AlertDao alertDao;
  @Autowired Sql sql;
  @Autowired ObjectMapper json;
  @Autowired SimulationService simulations;
  @Autowired BidmcImportService bidmc;

  @Test
  void healthAndAuthentication() throws Exception {
    mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/patients")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/session")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin@biopulse.local\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized());
    var result =
        mvc.perform(
                post("/api/v1/session")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"admin@biopulse.local\",\"password\":\"IntegrationOnlyPassword!\"}"))
            .andExpect(status().isOk())
            .andReturn();
    mvc.perform(
            get("/api/v1/patients")
                .session(
                    (org.springframework.mock.web.MockHttpSession)
                        result.getRequest().getSession()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].code").value("PT-0247"));
  }

  @Test
  @WithMockUser(username = "nurse@biopulse.local", roles = "NURSE")
  void roleAndWardAuthorization() throws Exception {
    mvc.perform(
            post("/api/v1/devices")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serial\":\"denied\",\"adapterType\":\"ECG\"}"))
        .andExpect(status().isForbidden());
    long ward = sql.insert("INSERT INTO wards(name) VALUES(?)", "Restricted " + UUID.randomUUID());
    long bed = sql.insert("INSERT INTO beds(ward_id,label) VALUES(?,'1')", ward);
    long p =
        registry.createPatient(new PatientCreate("X" + UUID.randomUUID(), "Restricted", null)).id();
    long a = registry.createAdmission(new AdmissionCreate(p, bed)).id();
    mvc.perform(get("/api/v1/admissions/" + a + "/measurements/latest"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/patients/" + p)).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "admin@biopulse.local", roles = "ADMIN")
  void jdbcBatchPipelineDuplicateAndAlertTransitions() throws Exception {
    var source =
        registry.createSource(new SourceCreate("Test", SourceType.DEVICE, "Integration fixture"));
    Instant t = Instant.now().minusSeconds(40).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    var rule =
        alerts.createRule(
            new RuleCreate(1, MeasurementCode.TEMP, null, 40d, 20, 15, AlertSeverity.WARNING));
    List<Observation> rows = new ArrayList<>();
    for (int sec : List.of(0, 10, 20))
      rows.add(
          new Observation(
              UUID.randomUUID().toString(),
              1,
              1,
              MeasurementCode.TEMP,
              41d,
              "Cel",
              t.plusSeconds(sec),
              source.id(),
              null,
              QualityFlag.GOOD));
    var saved = ingestion.ingest(rows);
    assertTrue(saved.stream().allMatch(m -> m.id() > 0));
    var history = measurements.history(1, MeasurementCode.TEMP, t, t.plusSeconds(30), 100, 0);
    assertEquals(3, history.size());
    assertEquals(41, history.getFirst().value());
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> ingestion.ingest(rows));
    assertEquals(
        3, measurements.history(1, MeasurementCode.TEMP, t, t.plusSeconds(30), 100, 0).size());
    var alert = alertDao.active(rule.id()).orElseThrow();
    assertEquals(AlertState.OPEN, alert.state());
    var ack = alerts.transition(alert.id(), 0, AlertState.ACKNOWLEDGED);
    assertEquals(1, ack.version());
    assertThrows(
        RuntimeException.class, () -> alerts.transition(alert.id(), 0, AlertState.RESOLVED));
    assertEquals(
        AlertState.RESOLVED, alerts.transition(alert.id(), 1, AlertState.RESOLVED).state());
    assertEquals(3, alertDao.events(alert.id()).size());
    mvc.perform(
            get("/api/v1/admissions/1/analytics")
                .param("from", t.toString())
                .param("to", t.plusSeconds(30).toString())
                .param("metric", "TEMP"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.count").value(3));
    mvc.perform(
            post("/api/v1/internal/simulator/measurements")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        List.of(
                            new Observation(
                                "bad",
                                1,
                                1,
                                MeasurementCode.HR,
                                null,
                                "bpm",
                                t,
                                source.id(),
                                null,
                                QualityFlag.GOOD)))))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(username = "admin@biopulse.local", roles = "ADMIN")
  void simulationPreservesRecordingTimeAndProvenance() {
    var session = simulations.create(new SimulationCreate(1, 1, 1));
    assertEquals(SimulatorState.CREATED, session.state());
    simulations.transition(session.id(), "start");
    simulations.tick(session.id());
    var output = registry.recording(session.outputRecordingId()).orElseThrow();
    var rows =
        measurements.recordingMeasurements(
            output.id(),
            Instant.parse("2026-10-25T08:30:00Z"),
            Instant.parse("2026-10-25T08:30:02Z"),
            100);
    assertFalse(rows.isEmpty());
    assertEquals(Instant.parse("2026-10-25T08:30:00Z"), rows.getFirst().observedAt());
    assertEquals(
        SourceType.SIMULATION, registry.source(rows.getFirst().sourceId()).orElseThrow().type());
    simulations.transition(session.id(), "pause");
    assertThrows(RuntimeException.class, () -> simulations.transition(session.id(), "start"));
    simulations.transition(session.id(), "resume");
    simulations.transition(session.id(), "stop");
    assertEquals(SimulatorState.STOPPED, simulations.get(session.id()).state());
  }

  @Test
  @WithMockUser(username = "admin@biopulse.local", roles = "ADMIN")
  void reportAndPaginationAndCsrf() throws Exception {
    mvc.perform(get("/api/v1/alerts").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.hasNext").value(true));
    mvc.perform(
            post("/api/v1/reports")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"admissionId\":1,\"from\":\"2026-10-25T08:30:00Z\",\"to\":\"2026-10-25T08:38:01Z\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNumber());
    mvc.perform(post("/api/v1/patients").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/patients").param("size", "-1")).andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(username = "admin@biopulse.local", roles = "ADMIN")
  void explicitMissingIsTheLatestRecordedState() throws Exception {
    var source =
        registry.createSource(
            new SourceCreate(
                "Latest-state regression",
                SourceType.DEVICE,
                "Unit fixture; recorded missing observation must override prior valid value"));
    var at = Instant.now().minusSeconds(2).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    ingestion.ingest(
        List.of(
            new Observation(
                UUID.randomUUID().toString(),
                1,
                1,
                MeasurementCode.PULSE,
                82d,
                "bpm",
                at.minusSeconds(1),
                source.id(),
                null,
                QualityFlag.GOOD),
            new Observation(
                UUID.randomUUID().toString(),
                1,
                1,
                MeasurementCode.PULSE,
                null,
                "bpm",
                at,
                source.id(),
                null,
                QualityFlag.MISSING)));
    mvc.perform(get("/api/v1/admissions/1/measurements/latest"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.metric == 'PULSE')].quality")
                .value(org.hamcrest.Matchers.contains("MISSING")))
        .andExpect(
            jsonPath("$[?(@.metric == 'PULSE')].value")
                .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
  }

  @Test
  @Transactional
  @WithMockUser(username = "admin@biopulse.local", roles = "ADMIN")
  void bidmcImportPreservesMissingValuesAndRejectsDuplicateId(@TempDir Path folder)
      throws Exception {
    String id = "bidmc_97";
    Files.writeString(
        folder.resolve(id + "_Fix.txt"),
        id + "\nSignals sampling frequency: 125 Hz\nNumerics sampling frequency: 1 Hz\n"
            + "MIMIC II matched wdb ID: research-pseudonym\n");
    var signal = new StringBuilder("Time [s], RESP, PLETH, II\n");
    for (int i = 0; i < 250; i++)
      signal.append(i / 125.0).append(",0.2,0.3,0.4\n");
    Files.writeString(folder.resolve(id + "_Signals.csv"), signal);
    Files.writeString(
        folder.resolve(id + "_Numerics.csv"),
        "Time [s], HR, PULSE, RESP, SpO2\n0,94,93,25,97\n1,,92,26,96\n");

    long ward = sql.insert("INSERT INTO wards(name) VALUES(?)", "Import " + UUID.randomUUID());
    long bed = sql.insert("INSERT INTO beds(ward_id,label) VALUES(?,'1')", ward);
    long patient =
        registry.createPatient(new PatientCreate("B" + UUID.randomUUID(), "Import fixture", null))
            .id();
    long admission = registry.createAdmission(new AdmissionCreate(patient, bed)).id();
    long ecg = registry.createDevice(new DeviceCreate("ECG-" + UUID.randomUUID(), "ECG")).id();
    long ox = registry.createDevice(new DeviceCreate("OX-" + UUID.randomUUID(), "OXIMETER")).id();
    long resp =
        registry.createDevice(new DeviceCreate("RESP-" + UUID.randomUUID(), "RESPIRATORY"))
            .id();
    for (long device : List.of(ecg, ox, resp))
      registry.assign(new AssignmentCreate(device, admission));
    var devices =
        Map.of(
            MeasurementCode.ECG, ecg,
            MeasurementCode.HR, ecg,
            MeasurementCode.PPG, ox,
            MeasurementCode.PULSE, ox,
            MeasurementCode.SPO2, ox,
            MeasurementCode.RESP, resp,
            MeasurementCode.RR, resp);

    var result = bidmc.importRecording(folder, id, "II", admission, devices);
    assertEquals(8, result.numericObservations());
    assertEquals("research-pseudonym", result.originalSubjectPseudonym());
    assertEquals(6, result.waveformChunks());
    var recorded =
        measurements.recordingMeasurements(
            result.recordingId(),
            BidmcImportService.RELATIVE_ANCHOR,
            BidmcImportService.RELATIVE_ANCHOR.plusSeconds(2),
            100);
    assertEquals(8, recorded.size());
    assertTrue(
        recorded.stream()
            .anyMatch(
                m ->
                    m.metric() == MeasurementCode.HR
                        && m.quality() == QualityFlag.MISSING
                        && m.value() == null));
    assertThrows(
        DataIntegrityViolationException.class,
        () -> bidmc.importRecording(folder, id, "II", admission, devices));
  }
}
