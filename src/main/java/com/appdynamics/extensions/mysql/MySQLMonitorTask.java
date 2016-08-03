package com.appdynamics.extensions.mysql;

import com.appdynamics.TaskInputArgs;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.crypto.CryptoUtil;
import com.google.common.collect.Maps;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Satish Muddam
 */
public class MySQLMonitorTask implements Runnable {

    private static final Logger logger = Logger.getLogger(MySQLMonitor.class);

    private Map server;
    private MonitorConfiguration configuration;
    private Map<String, Map<String, String>> cachedStats;

    public MySQLMonitorTask(Map server, MonitorConfiguration configuration, Map<String, Map<String, String>> cachedStats) {
        this.server = server;
        this.configuration = configuration;
        this.cachedStats = cachedStats;
    }

    public void run() {

        String name = (String) server.get("name");
        String host = (String) server.get("host");
        int port = (Integer) server.get("port");

        try {
            Map<String, String> stats = populate(new String[]{"show global variables", "show global status"});
            List<Map> slaves = (List<Map>) server.get("slave");

            if (slaves != null && slaves.size() > 0) {
                logger.debug("Collecting replication metrics");
                populateReplicationStats("show slave status");

            }

            Map<String, String> prevStats = cachedStats.get(host + ":" + port);
            if (prevStats == null) {
                prevStats = new HashMap<String, String>();
            }

            printMetrics(stats, prevStats);
            cachedStats.put(host + ":" + port, stats);

        } catch (TaskExecutionException e) {
            logger.error("Error while collecting metrics from [ " + name + " ]");
        }
    }


    private void printMetric(String metricName, String metricValue, String aggregation,
                             String timeRollup, String cluster) {

        String metricPrefix = configuration.getMetricPrefix();

        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Sending [%s/%s/%s] metric = {%s, %s}",
                    aggregation, timeRollup, cluster,
                    metricPrefix + metricName, metricValue));
        }
        configuration.getMetricWriter().printMetric(metricPrefix + "|" + metricName, metricValue, aggregation, timeRollup, cluster);
    }

    private void printReplicationMetrics(Map<String, String> replicationStats) {
        if (replicationStats != null) {

            for (Map.Entry<String, String> entry : replicationStats.entrySet()) {
                printMetric(entry.getKey(), entry.getValue(),
                        MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                        MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
            }

        }
    }

    private void printMetrics(Map<String, String> stats, Map<String, String> prevStats) {

        Utils utils = new Utils(prevStats, stats);

        // just for debug output
        logger.debug("Starting METRIC COLLECTION for Mysql Monitor.......");


        // RESOURCE UTILIZATION
        printMetric("Resource Utilization|% Connections Used", utils.getPercent("max_used_connections", "max_connections"),
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Resource Utilization|Total Active Threads", utils.getString("Threads_running"),
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Resource Utilization|Total Cached Threads", utils.getString("Threads_cached"),
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Resource Utilization|Total Connected Threads", utils.getString("Threads_connected"),
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        /*
         * printMetric("Resource Utilization|Transactions|In Progress",
		 * getString(getDiffValue("Com_begin") - (getDiffValue("Com_rollback") +
		 * getDiffValue("Com_commit"))),
		 * MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
		 * MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
		 * MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL); hard to figure
		 * this out...need to get from raw innodb status :-(
		 */
        printMetric("Resource Utilization|Open Tables", utils.getString("Open_tables"),
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Resource Utilization|% Key Cache Used", utils.getPercent(utils.getValue("key_buffer_size")
                        - (utils.getValue("key_blocks_unused") * utils.getValue("key_cache_block_size")), utils.getValue("key_buffer_size")),
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
        printMetric("Resource Utilization|% Query Cache Used", utils.getPercent(utils.getValue("query_cache_size")
                        - utils.getValue("Qcache_free_memory"), utils.getValue("query_cache_size")),
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
        printMetric("Resource Utilization|% InnoDB Buffer Pool Used", utils.getPercent(
                utils.getValue("innodb_buffer_pool_pages_total") - utils.getValue("innodb_buffer_pool_pages_free"),
                utils.getValue("innodb_buffer_pool_pages_total")), MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
        printMetric("Resource Utilization|Log File % of InnoDB Buffer Pool", utils.getPercent(
                utils.getValue("Innodb_log_file_size") * utils.getValue("Innodb_log_files_in_group"),
                utils.getValue("innodb_buffer_pool_pages_total") * (2 * 8192)),
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);

        // ACTIVITY
        printMetric("Activity|Connections|Total Attempts", utils.getString(utils.getDiffValue("Connections")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|Connections|Total Failed+Aborted", utils.getString(utils.getDiffValue("aborted_clients")
                        + utils.getDiffValue("aborted_connects")), MetricWriter.METRIC_AGGREGATION_TYPE_SUM,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric(
                "Activity|Transactions|Total", // this doesn't count auto-commit
                // ones!
                utils.getString(utils.getDiffValue("Com_rollback") + utils.getDiffValue("Com_commit")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|Bytes between Client-Server|Sent", utils.getString(utils.getDiffValue("bytes_sent")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|Bytes between Client-Server|Received", utils.getString(utils.getDiffValue("Bytes_received")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|InnoDB|Bytes Read", utils.getString(utils.getDiffValue("Innodb_data_read")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|InnoDB|Bytes Written", utils.getString(utils.getDiffValue("Innodb_data_written")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|InnoDB|File Synchronization", utils.getString(utils.getDiffValue("Innodb_data_fsyncs")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|Tables|Opened", utils.getString(utils.getDiffValue("Opened_tables")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|Tables|Temp Created", utils.getString(utils.getDiffValue("Created_tmp_disk_tables")
                        + utils.getDiffValue("Created_tmp_tables")), MetricWriter.METRIC_AGGREGATION_TYPE_SUM,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|Transactions|Rolled Back", utils.getString(utils.getDiffValue("Com_rollback")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|Transactions|Committed", utils.getString(utils.getDiffValue("Com_commit")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);

        printMetric("Activity|Operations|Total Requests", utils.getString(utils.getDiffValue("Questions")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|Operations|Total Inserts", utils.getString(utils.getDiffValue("Com_insert")
                        + utils.getDiffValue("Com_replace")), MetricWriter.METRIC_AGGREGATION_TYPE_SUM,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|Operations|Total Queries", utils.getString(utils.getDiffValue("Com_select")
                        + utils.getDiffValue("Qcache_hits")), MetricWriter.METRIC_AGGREGATION_TYPE_SUM,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|Operations|Total Deletes", utils.getString(utils.getDiffValue("Com_delete")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|Operations|Total Updates", utils.getString(utils.getDiffValue("Com_update")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);

        printMetric("Activity|Operations|InnoDB|Total Rows Queried", utils.getString(utils.getDiffValue("innodb_rows_read")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|Operations|InnoDB|Total Rows Deleted", utils.getString(utils.getDiffValue("innodb_rows_deleted")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|Operations|InnoDB|Total Rows Updated", utils.getString(utils.getDiffValue("innodb_rows_updated")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|Operations|InnoDB|Total Rows Inserted", utils.getString(utils.getDiffValue("innodb_rows_inserted")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Activity|Sort Total", utils.getString(utils.getDiffValue("sort_scan") + utils.getDiffValue("sort_range")),
                MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);

        // EFFICIENCY
        printMetric("Efficiency|Index|Single Table", utils.getPercent(utils.getDiffValue("select_range"),
                (utils.getDiffValue("select_range") + utils.getDiffValue("select_scan"))),
                MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE, MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
        printMetric("Efficiency|Index|Joins",
                utils.getPercent(utils.getDiffValue("select_full_join"), (utils.getDiffValue("select_full_join")
                        + utils.getDiffValue("select_range_check") + utils.getDiffValue("select_full_range_join"))),
                MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE, MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
        printMetric("Efficiency|Sort|Spill to Disk", utils.getPercent(utils.getDiffValue("sort_merge_passes"),
                utils.getDiffValue("sort_scan_sort_range")), MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
        printMetric("Efficiency|Query|Slow", utils.getPercent(utils.getDiffValue("Slow_queries"), utils.getDiffValue("Com_select")),
                MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE, MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
        printMetric("Efficiency|Cache|Key Cache|Read Hits", utils.getReversePercent(utils.getDiffValue("key_reads"),
                utils.getDiffValue("key_read_requests")), MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
        printMetric("Efficiency|Cache|Key Cache|Write Hits", utils.getReversePercent(utils.getDiffValue("key_writes"),
                utils.getDiffValue("key_write_requests")), MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
        printMetric("Efficiency|Cache|Query Cache|Hits", utils.getPercent(utils.getDiffValue("Qcache_hits"),
                utils.getDiffValue("Com_select")), MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
        printMetric("Efficiency|InnoDB|Cache|Buffer Pool|Hits", utils.getReversePercent(
                utils.getDiffValue("Innodb_buffer_pool_reads"), utils.getDiffValue("Innodb_buffer_pool_read_requests")),
                MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE, MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
        printMetric("Efficiency|Cache|Threads|Hits", utils.getReversePercent(utils.getDiffValue("threads_created"),
                utils.getDiffValue("Connections")), MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
        printMetric("Efficiency|Table Locks|Waited", utils.getPercent(utils.getDiffValue("table_locks_waited"),
                utils.getDiffValue("table_locks_waited") + utils.getDiffValue("table_locks_immediate")),
                MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE, MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
        printMetric("Efficiency|InnoDB|Row Locks|Waiting", utils.getString(utils.getValue("Innodb_row_lock_current_waits")),
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Efficiency|InnoDB|Row Locks|Row Lock Time", utils.getString(utils.getValue("Innodb_row_lock_time")),
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Efficiency|InnoDB|Row Locks|Avg Time Waited", utils.getString(utils.getValue("getInnodb_row_lock_time_avg")),
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
        printMetric("Efficiency|Binary Log|% Transactions too Big", utils.getPercent(utils.getDiffValue("Binlog_cache_disk_use"),
                utils.getDiffValue("Binlog_cache_use")), MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE,
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
        printMetric("Efficiency|Tables|Temp|% Created on Disk", utils.getPercent(utils.getDiffValue("Created_tmp_disk_tables"),
                utils.getDiffValue("Created_tmp_disk_tables") + utils.getDiffValue("Created_tmp_tables")),
                MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE, MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
        printMetric("Efficiency|InnoDB|Cache|Buffer Pool|% Writes Waited", utils.getPercent(
                utils.getDiffValue("Innodb_buffer_pool_wait_free"), utils.getDiffValue("innodb_buffer_pool_write_requests")),
                MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE, MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);


        printMetric("Resource Utilization|Opened Files", utils.getString("Opened_files"),
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Resource Utilization|Slow Queries", utils.getString("Slow_queries"),
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Resource Utilization|Max Used Connections", utils.getString("Max_used_connections"),
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Resource Utilization|Aborted Connections", utils.getString("Aborted_connects"),
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
        printMetric("Resource Utilization|Aborted Clients", utils.getString("Aborted_clients"),
                MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
    }

    private Connection connect(String host, int port, String user, String password) throws TaskExecutionException {
        String connStr = "jdbc:mysql://" + host + ":" + port;
        connStr += "/information_schema?";

        if ((user != null) && (!user.equals("")))
            connStr += "user=" + user;
        else
            connStr += "user=root";

        String logConnStr = connStr;
        if ((password != null) && (!password.equals(""))) {
            connStr += "&password=" + password;
            logConnStr += "&password=YES";
        }

        logger.debug("Connecting to: " + logConnStr);

        Connection conn = null;
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            conn = DriverManager.getConnection(connStr);
            logger.debug("Successfully connected to Mysql DB");
        } catch (ClassNotFoundException e) {
            logger.error("Unable to load the driver class", e);
            throw new TaskExecutionException("Unable to load the driver class", e);
        } catch (SQLException e) {
            logger.error("Unable to create connection", e);
            throw new TaskExecutionException("Unable to create connection", e);
        } catch (InstantiationException e) {
            logger.error("Unable to instantiate driver class", e);
            throw new TaskExecutionException("Unable to instantiate driver class", e);
        } catch (IllegalAccessException e) {
            logger.error("Unable to load the driver class", e);
            throw new TaskExecutionException("Unable to load the driver class", e);
        }
        return conn;
    }

    private String getPassword(String password, String passwordEncrypted, String encryptionKey) {

        if (password == null || password.length() <= 0) {
            try {
                Map<String, String> args = Maps.newHashMap();
                args.put(TaskInputArgs.PASSWORD_ENCRYPTED, passwordEncrypted);
                args.put(TaskInputArgs.ENCRYPTION_KEY, encryptionKey);
                password = CryptoUtil.getPassword(args);

            } catch (IllegalArgumentException e) {
                String msg = "Encryption Key not specified. Please set the value in config.yaml.";
                logger.error(msg);
                throw new IllegalArgumentException(msg);
            }
        }
        return password;
    }

    private void populateReplicationStats(String query) {

        Map<String, String> slaveMetricsMap = new HashMap<String, String>();

        //List<String> slaveMetrics = new ArrayList<String>();
        //slaveMetrics.add("Slave_IO_Running");
        //slaveMetrics.add("SQL_Delay");

        List<Map> slaves = (List<Map>) server.get("slave");


        for (Map slave : slaves) {
            Connection conn = null;
            Statement stmt = null;

            String name = (String) slave.get("name");
            String host = (String) slave.get("host");
            int port = (Integer) slave.get("port");
            String user = (String) slave.get("user");
            String password = (String) slave.get("password");
            String passwordEncrypted = (String) slave.get("passwordEncrypted");
            String encryptionKey = (String) slave.get("encryptionKey");

            String plainPassword = getPassword(password, passwordEncrypted, encryptionKey);
            try {
                conn = connect(host, port, user, plainPassword);
                stmt = conn.createStatement();
                ResultSet resultSet = stmt.executeQuery(query);
                while (resultSet.next()) {
                    String value = resultSet.getString("Slave_IO_Running");

                    int state = 0;
                    if ("Yes".equalsIgnoreCase(value)) {
                        state = 1;
                    }
                    slaveMetricsMap.put("Replication|" + name + "|Slave IO Running", String.valueOf(state));

                    value = resultSet.getString("SQL_Delay");
                    slaveMetricsMap.put("Replication|" + name + "|SQL Delay", String.valueOf(value));
                }

                printReplicationMetrics(slaveMetricsMap);

            } catch (TaskExecutionException e) {
                logger.error("Error connecting to slave machine", e);
            } catch (SQLException e) {
                logger.error("Error querying slave machine", e);
            } finally {
                closeStatement(stmt);
                closeConnection(conn);
            }
        }
    }

    private Map<String, String> populate(String[] queries) throws TaskExecutionException {
        Connection conn = null;
        Statement stmt = null;

        Map<String, String> statsMap = new HashMap<String, String>();
        String name = (String) server.get("name");

        try {
            String host = (String) server.get("host");
            int port = (Integer) server.get("port");
            String user = (String) server.get("user");
            String password = (String) server.get("password");
            String passwordEncrypted = (String) server.get("passwordEncrypted");
            String encryptionKey = (String) server.get("encryptionKey");

            String plainPassword = getPassword(password, passwordEncrypted, encryptionKey);
            conn = connect(host, port, user, plainPassword);
            stmt = conn.createStatement();

            for (String query : queries) {
                ResultSet rs = null;

                try {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Executing query [" + query + "]");
                    }

                    rs = stmt.executeQuery(query);

                    while (rs.next()) {
                        String key = rs.getString(1);
                        String value = rs.getString(2);

                        if (logger.isDebugEnabled()) {
                            logger.debug("[key,value] = [" + key + "," + value + "]");
                        }

                        statsMap.put(key.toUpperCase(), value);
                    }
                } catch (Exception ex) { //Log error and try the next query
                    logger.error("Error while executing query [" + query + "] on [ " + name + "]", ex);
                } finally {
                    closeResultSet(rs);
                }
            }

        } catch (SQLException e) {
            logger.error("Error while creating connection to [" + name + "]", e);
            throw new TaskExecutionException("Error while creating connection to [" + name + "]", e);
        } finally {
            closeStatement(stmt);
            closeConnection(conn);
        }
        return statsMap;
    }

    private void closeResultSet(ResultSet rs) {
        if (rs == null) {
            return;
        }

        try {
            rs.close();
        } catch (SQLException e) {
            logger.error("Unable to close the ResultSet", e);
        }
    }

    private void closeStatement(Statement stmt) {
        if (stmt == null) {
            return;
        }
        try {
            stmt.close();
        } catch (SQLException e) {
            logger.error("Unable to close the Statement", e);
        }
    }

    private void closeConnection(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.close();
        } catch (SQLException e) {
            logger.error("Unable to close the Connection", e);
        }
    }
}