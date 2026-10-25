package org.biopulse.simulation;
import java.io.*;import java.util.*;import org.biopulse.dto.Requests.Observation;
public interface DatasetImporter {List<Observation> parse(InputStream input)throws IOException;}
