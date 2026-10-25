package org.biopulse.analytics;
import java.time.*;import java.util.*;import org.biopulse.domain.*;
public final class Statistics {
 private Statistics(){}
 public record Gap(Instant from,Instant to,double seconds){}
 public record Point(Instant at,Double value){}
 public static boolean usable(Measurement m){return m.value()!=null&&Double.isFinite(m.value())&&(m.quality()==QualityFlag.GOOD||m.quality()==QualityFlag.FAIR);}
 /** Sample-weighted statistics; coverage is union of forward cadence intervals, clipped to the requested window. */
 public static AnalyticsSnapshot summarize(long admission,MeasurementCode metric,List<Measurement> input,Instant from,Instant to,Duration cadence,Double low,Double high,Instant now){
  if(!to.isAfter(from)||cadence.isZero()||cadence.isNegative())throw new IllegalArgumentException("Invalid range/cadence");
  var all=input.stream().filter(m->m.metric()==metric&&!m.observedAt().isBefore(from)&&m.observedAt().isBefore(to)).sorted(Comparator.comparing(Measurement::observedAt).thenComparingLong(Measurement::id)).toList();
  // At coincident timestamps the last recorded event wins for temporal coverage, preserving every row in storage.
  var unique=new TreeMap<Instant,Measurement>();all.forEach(m->unique.put(m.observedAt(),m));
  var valid=unique.values().stream().filter(Statistics::usable).toList();double[] values=valid.stream().mapToDouble(Measurement::value).sorted().toArray();int n=values.length;
  Double mean=n==0?null:Arrays.stream(values).average().orElseThrow();double variance=n==0?0:Arrays.stream(values).map(v->Math.pow(v-mean,2)).average().orElseThrow();
  List<Gap> gaps=new ArrayList<>();Instant covered=from;double seconds=0,deviation=0;
  var timeline=new ArrayList<>(unique.values());
  for(int i=0;i<timeline.size();i++){var m=timeline.get(i);if(!usable(m))continue;Instant end=m.observedAt().plus(cadence);if(i+1<timeline.size()&&timeline.get(i+1).observedAt().isBefore(end))end=timeline.get(i+1).observedAt();if(end.isAfter(to))end=to;
   Instant start=m.observedAt().isBefore(covered)?covered:m.observedAt();if(start.isAfter(covered))gaps.add(gap(covered,start));if(end.isAfter(start)){double d=duration(start,end);seconds+=d;if((low!=null&&m.value()<low)||(high!=null&&m.value()>high))deviation+=d;}if(end.isAfter(covered))covered=end;
  }
  if(covered.isBefore(to))gaps.add(gap(covered,to));
  Double slope=null;if(n>=2){double xmean=valid.stream().mapToDouble(m->duration(from,m.observedAt())).average().orElseThrow(),num=0,den=0;for(var m:valid){double x=duration(from,m.observedAt())-xmean;num+=x*(m.value()-mean);den+=x*x;}if(den>0)slope=num/den;}
  boolean fresh=!timeline.isEmpty()&&usable(timeline.getLast())&&timeline.getLast().observedAt().isAfter(now.minus(cadence.multipliedBy(2)));
  return new AnalyticsSnapshot(admission,metric,from,to,n,n==0?null:values[0],n==0?null:values[n-1],mean,n==0?null:(n%2==0?(values[n/2-1]+values[n/2])/2:values[n/2]),n==0?null:Math.sqrt(variance),slope,100*seconds/duration(from,to),deviation,List.copyOf(gaps),fresh);
 }
 public static List<Point> rollingAverage(List<Measurement> rows,Duration window){if(window.isNegative()||window.isZero())throw new IllegalArgumentException("Invalid window");List<Point> result=new ArrayList<>();var sorted=rows.stream().sorted(Comparator.comparing(Measurement::observedAt)).toList();for(var row:sorted){var avg=sorted.stream().filter(Statistics::usable).filter(m->!m.observedAt().isAfter(row.observedAt())&&m.observedAt().isAfter(row.observedAt().minus(window))).mapToDouble(Measurement::value).average();result.add(new Point(row.observedAt(),avg.isPresent()?avg.getAsDouble():null));}return result;}
 private static Gap gap(Instant a,Instant b){return new Gap(a,b,duration(a,b));}public static double duration(Instant a,Instant b){return Duration.between(a,b).toNanos()/1e9;}
}