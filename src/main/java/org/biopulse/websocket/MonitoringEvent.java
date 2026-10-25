package org.biopulse.websocket;
import java.time.Instant;import java.util.UUID;import org.biopulse.domain.*;
public record MonitoringEvent(String eventId,String type,Long admissionId,Instant timestamp,MeasurementCode metric,Double value,String unit,SourceType sourceType,QualityFlag quality,Object payload) {
 public static MonitoringEvent of(String type,Long admission,MeasurementCode metric,Double value,String unit,SourceType source,QualityFlag quality,Object payload){return new MonitoringEvent(UUID.randomUUID().toString(),type,admission,Instant.now(),metric,value,unit,source,quality,payload);}
}