package org.biopulse.websocket;
import org.springframework.stereotype.Component;import org.springframework.context.ApplicationEventPublisher;
@Component public class SpringMonitoringEventPublisher implements MonitoringEventPublisher {
 private final ApplicationEventPublisher publisher;public SpringMonitoringEventPublisher(ApplicationEventPublisher publisher){this.publisher=publisher;}public void publish(MonitoringEvent event){publisher.publishEvent(event);}
}