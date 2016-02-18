package com.sogou.flume.sink.hive;

import com.sogou.flume.sink.hive.deserializer.Deserializer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.io.orc.OrcFile;
import org.apache.hadoop.hive.ql.io.orc.Writer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Created by Tao Li on 2/16/16.
 */
public class HiveBatchWriter {
  private static final Logger LOG = LoggerFactory.getLogger(HiveBatchWriter.class);

  private long lastWriteTime = -1;  // -1: no data write yet
  private Writer writer;
  private Deserializer deserializer;
  private String file;
  private long idleTimeout = 5000;
  private List<Callback> initCallbacks = null;
  private List<Callback> closeCallbacks = null;

  public interface Callback {
    void run();
  }

  public HiveBatchWriter(Configuration conf, Deserializer deserializer, String location,
                         long idleTimeout) throws IOException {
    this(conf, deserializer, location, idleTimeout, null, null);
  }

  public HiveBatchWriter(Configuration conf, Deserializer deserializer, String file,
                         long idleTimeout,
                         List<Callback> initCallbacks, List<Callback> closeCallbacks)
      throws IOException {
    this.deserializer = deserializer;
    this.file = file;
    this.idleTimeout = idleTimeout;
    this.initCallbacks = initCallbacks;
    this.closeCallbacks = closeCallbacks;
    OrcFile.WriterOptions writerOptions = OrcFile.writerOptions(conf);
    writerOptions.inspector(deserializer.getObjectInspector());
    this.writer = OrcFile.createWriter(new Path(file), writerOptions);

    if (initCallbacks != null) {
      for (Callback callback : initCallbacks) {
        callback.run();
      }
    }
  }

  public void append(byte[] bytes) throws IOException {
    writer.addRow(deserializer.deserialize(bytes));
    lastWriteTime = System.currentTimeMillis();
  }

  public void close() throws IOException {
    writer.close();

    if (closeCallbacks != null) {
      for (Callback callback : closeCallbacks) {
        callback.run();
      }
    }
  }

  public boolean isIdle() {
    return lastWriteTime > 0 && System.currentTimeMillis() - lastWriteTime >= idleTimeout;
  }

  public String getFile() {
    return file;
  }
}
