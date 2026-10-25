package org.biopulse.dao;
import java.util.*;
import org.biopulse.domain.*;
import org.biopulse.dto.Requests.*;
public interface RegistryDao {
 List<Patient> patients(int offset,int limit); Optional<Patient> patient(long id); Patient createPatient(PatientCreate request);
 List<Admission> admissions(int offset,int limit); Optional<Admission> admission(long id); Admission createAdmission(AdmissionCreate request);
 List<MedicalDevice> devices(); Optional<MedicalDevice> device(long id); MedicalDevice createDevice(DeviceCreate request);
 DeviceAssignment assign(AssignmentCreate request); boolean assigned(long admissionId,long deviceId,java.time.Instant at);
 Optional<DataSource> source(long id); List<DataSource> sources(); DataSource createSource(SourceCreate r);
 Optional<SourceRecording> recording(long id); List<SourceRecording> recordings(); SourceRecording createRecording(RecordingCreate r);
 List<Ward> wards(); List<Bed> beds(); void lockAdmission(long id);
}