package org.biopulse.simulation;
import org.springframework.stereotype.Component;import org.springframework.scheduling.annotation.Scheduled;import org.biopulse.dao.SimulationDao;import org.biopulse.service.SimulationService;
@Component public class SimulationScheduler {
 private final SimulationDao dao;private final SimulationService service;public SimulationScheduler(SimulationDao dao,SimulationService service){this.dao=dao;this.service=service;}
 @Scheduled(fixedDelay=1000) public void run(){for(long id:dao.running())try{service.tick(id);}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).error("Playback tick failed for session {}; cursor retained",id,e);}}
}