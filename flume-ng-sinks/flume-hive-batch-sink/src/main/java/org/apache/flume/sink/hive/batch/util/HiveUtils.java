package org.apache.flume.sink.hive.batch.util;

import com.google.common.base.Joiner;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.*;
import org.apache.hadoop.hive.serde.serdeConstants;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by Tao Li on 2/16/16.
 */
public class HiveUtils {
  private static final Logger LOG = LoggerFactory.getLogger(HiveUtils.class);

  private static HiveConf hiveConf = new HiveConf();

  public static HiveMetaStoreClient createHiveMetaStoreClient() throws MetaException {
    return new HiveMetaStoreClient(hiveConf);
  }

  public static void closeHiveMetaStoreClient(HiveMetaStoreClient client) {
    if (client != null)
      client.close();
  }

  public static void addPartition(HiveMetaStoreClient client,
                                  String dbName, String tableName,
                                  List<String> values, String location) throws TException {
    int createTime = (int) (System.currentTimeMillis() / 1000);
    int lastAccessTime = 0;
    Map<String, String> parameters = new HashMap<String, String>();

    List<FieldSchema> cols = client.getFields(dbName, tableName);
    String inputFormat = "org.apache.hadoop.hive.ql.io.orc.OrcInputFormat";
    String outputFormat = "org.apache.hadoop.hive.ql.io.orc.OrcOutputFormat";
    boolean compressed = false;
    int numBuckets = -1;
    Map<String, String> serDeInfoParameters = new HashMap<String, String>();
    serDeInfoParameters.put("serialization.format", "1");
    SerDeInfo serDeInfo = new SerDeInfo(null, "org.apache.hadoop.hive.ql.io.orc.OrcSerde", serDeInfoParameters);
    List<String> bucketCols = new ArrayList<String>();
    List<Order> sortCols = new ArrayList<Order>();
    Map<String, String> sdParameters = new HashMap<String, String>();
    StorageDescriptor sd = new StorageDescriptor(cols, location, inputFormat, outputFormat,
        compressed, numBuckets, serDeInfo, bucketCols, sortCols, sdParameters);

    Partition partition = new Partition(values, dbName, tableName, createTime, lastAccessTime, sd, parameters);
    List<Partition> partitions = client.listPartitions(
        partition.getDbName(), partition.getTableName(), partition.getValues(), (short) 1);
    if (partitions.size() != 0) {
      LOG.info(String.format("partition already exist: %s.%s, %s",
          partition.getDbName(), partition.getTableName(), partition.getValues()));
    } else {
      client.add_partition(partition);
    }
  }

  public static List<FieldSchema> getFields(HiveMetaStoreClient client,
                                            String dbName, String tableName) throws TException {
    return client.getFields(dbName, tableName);
  }

  public static Table getTable(HiveMetaStoreClient client,
                               String dbName, String tableName) throws TException {
    return client.getTable(dbName, tableName);
  }

  public static Properties getTableProperties(HiveMetaStoreClient client,
                                              String dbName, String tableName) throws TException {
    Properties properties = new Properties();

    Table table = getTable(client, dbName, tableName);
    SerDeInfo serDeInfo = table.getSd().getSerdeInfo();
    for (Map.Entry<String, String> entry : serDeInfo.getParameters().entrySet()) {
      properties.setProperty(entry.getKey(), entry.getValue());
    }

    List<FieldSchema> fields = HiveUtils.getFields(dbName, tableName);
    List<String> columnNames = new ArrayList<String>();
    List<String> columnTypes = new ArrayList<String>();
    for (FieldSchema field : fields) {
      columnNames.add(field.getName());
      columnTypes.add(field.getType());
    }
    String columnNameProperty = Joiner.on(",").join(columnNames);
    String columnTypeProperty = Joiner.on(",").join(columnTypes);
    properties.setProperty(serdeConstants.LIST_COLUMNS, columnNameProperty);
    properties.setProperty(serdeConstants.LIST_COLUMN_TYPES, columnTypeProperty);

    return properties;
  }

  public static void addPartition(String dbName, String tableName,
                                  List<String> values, String location) throws TException {
    HiveMetaStoreClient client = null;
    try {
      client = createHiveMetaStoreClient();
      addPartition(client, dbName, tableName, values, location);
    } finally {
      closeHiveMetaStoreClient(client);
    }
  }

  public static List<FieldSchema> getFields(String dbName, String tableName) throws TException {
    HiveMetaStoreClient client = null;
    try {
      client = createHiveMetaStoreClient();
      return getFields(client, dbName, tableName);
    } finally {
      closeHiveMetaStoreClient(client);
    }
  }

  public static Table getTable(String dbName, String tableName) throws TException {
    HiveMetaStoreClient client = null;
    try {
      client = createHiveMetaStoreClient();
      return getTable(client, dbName, tableName);
    } finally {
      closeHiveMetaStoreClient(client);
    }
  }

  public static Properties getTableProperties(String dbName, String tableName) throws TException {
    HiveMetaStoreClient client = null;
    try {
      client = createHiveMetaStoreClient();
      return getTableProperties(client, dbName, tableName);
    } finally {
      closeHiveMetaStoreClient(client);
    }
  }
}
