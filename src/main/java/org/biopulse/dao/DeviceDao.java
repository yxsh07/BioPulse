package org.biopulse.dao;

import java.util.List;
import java.util.Optional;
import org.biopulse.domain.DeviceAssignment;
import org.biopulse.domain.MedicalDevice;
import org.biopulse.dto.Requests.AssignmentCreate;
import org.biopulse.dto.Requests.DeviceCreate;

public interface DeviceDao {
  MedicalDevice create(DeviceCreate device);

  Optional<MedicalDevice> find(long id);

  List<MedicalDevice> all();

  DeviceAssignment assign(AssignmentCreate assignment);

  List<DeviceAssignment> assignmentHistory(long deviceId);

  void setOnline(long deviceId, boolean online);

  List<Long> assignedAdmissions(long deviceId);
}
