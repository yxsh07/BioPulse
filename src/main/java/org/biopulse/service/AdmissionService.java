package org.biopulse.service;
import org.biopulse.dao.RegistryDao;import org.biopulse.domain.Admission;import org.biopulse.dto.Requests.AdmissionCreate;import org.biopulse.security.AccessPolicy;import org.biopulse.exception.ApiException;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service public class AdmissionService {
 private final RegistryDao dao;private final AccessPolicy access;public AdmissionService(RegistryDao dao,AccessPolicy access){this.dao=dao;this.access=access;}
 public Admission get(long id){Admission a=dao.admission(id).orElseThrow(()->ApiException.missing("Admission"));access.admission(id);return a;}
 @Transactional public Admission create(AdmissionCreate r){Admission a=dao.createAdmission(r);access.audit("CREATE_ADMISSION","admission:"+a.id());return a;}
}