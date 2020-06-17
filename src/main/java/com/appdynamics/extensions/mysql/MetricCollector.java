package com.appdynamics.extensions.mysql;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.mysql.config.MetricConfig;
import com.appdynamics.extensions.mysql.config.Stat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;


import static com.appdynamics.extensions.mysql.Utility.Constants.SEPARATOR;

/**
 * Created by abhishek.saxena on 17/06/20.
 */

public class MetricCollector {

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(MetricCollector.class);

    private Stat[] stat;
    private String metricPrefix;

    private static ObjectMapper objectMapper = new ObjectMapper();



    public MetricCollector(Stat[] stat,String metricPrefix){
        this.stat=stat;
        this.metricPrefix=metricPrefix;
    }


    protected List<Metric> collectMetrics(Map<String, String> stats, Map<String, String> prevStats) {
        Utils utils = new Utils(prevStats,stats);
        Map<String,String> collectMetric = Maps.newHashMap();

        logger.debug("Starting METRIC COLLECTION for Mysql Monitor.......");

        // RESOURCE UTILIZATION
        collectMetric.put("Resource Utilization|% Connections Used", utils.getPercent("max_used_connections", "max_connections"));
        collectMetric.put("Resource Utilization|Total Active Threads", utils.getString("Threads_running"));
        collectMetric.put("Resource Utilization|Total Cached Threads", utils.getString("Threads_cached"));
        collectMetric.put("Resource Utilization|Total Connected Threads", utils.getString("Threads_connected"));
        /*
         * printMetric("Resource Utilization|Transactions|In Progress",
         * getString(getDiffValue("Com_begin") - (getDiffValue("Com_rollback") +
         * getDiffValue("Com_commit"))),
         * MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
         * MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
         * MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL); hard to figure
         * this out...need to get from raw innodb status :-(
         */
        collectMetric.put("Resource Utilization|Open Tables", utils.getString("Open_tables"));
        collectMetric.put("Resource Utilization|% Key Cache Used", utils.getPercent(utils.getValue("key_buffer_size")
                - (utils.getValue("key_blocks_unused") * utils.getValue("key_cache_block_size")), utils.getValue("key_buffer_size")));
        collectMetric.put("Resource Utilization|% Query Cache Used", utils.getPercent(utils.getValue("query_cache_size")
                - utils.getValue("Qcache_free_memory"), utils.getValue("query_cache_size")));
        collectMetric.put("Resource Utilization|% InnoDB Buffer Pool Used", utils.getPercent(
                utils.getValue("innodb_buffer_pool_pages_total") - utils.getValue("innodb_buffer_pool_pages_free"),
                utils.getValue("innodb_buffer_pool_pages_total")));
        collectMetric.put("Resource Utilization|Log File % of InnoDB Buffer Pool", utils.getPercent(
                utils.getValue("Innodb_log_file_size") * utils.getValue("Innodb_log_files_in_group"),
                utils.getValue("innodb_buffer_pool_pages_total") * (2 * 8192)));

        // ACTIVITY
        collectMetric.put("Activity|Connections|Total Attempts", utils.getString(utils.getDiffValue("Connections")));
        collectMetric.put("Activity|Connections|Total Failed+Aborted", utils.getString(utils.getDiffValue("aborted_clients")
                + utils.getDiffValue("aborted_connects")));
        collectMetric.put(
                "Activity|Transactions|Total", // this doesn't count auto-commit
                // ones!
                utils.getString(utils.getDiffValue("Com_rollback") + utils.getDiffValue("Com_commit")));
        collectMetric.put("Activity|Bytes between Client-Server|Sent", utils.getString(utils.getDiffValue("bytes_sent")));
        collectMetric.put("Activity|Bytes between Client-Server|Received", utils.getString(utils.getDiffValue("Bytes_received")));
        collectMetric.put("Activity|InnoDB|Bytes Read", utils.getString(utils.getDiffValue("Innodb_data_read")));
        collectMetric.put("Activity|InnoDB|Bytes Written", utils.getString(utils.getDiffValue("Innodb_data_written")));
        collectMetric.put("Activity|InnoDB|File Synchronization", utils.getString(utils.getDiffValue("Innodb_data_fsyncs")));
        collectMetric.put("Activity|Tables|Opened", utils.getString(utils.getDiffValue("Opened_tables")));
        collectMetric.put("Activity|Tables|Temp Created", utils.getString(utils.getDiffValue("Created_tmp_disk_tables")
                + utils.getDiffValue("Created_tmp_tables")));
        collectMetric.put("Activity|Transactions|Rolled Back", utils.getString(utils.getDiffValue("Com_rollback")));
        collectMetric.put("Activity|Transactions|Committed", utils.getString(utils.getDiffValue("Com_commit")));

        collectMetric.put("Activity|Operations|Total Requests", utils.getString(utils.getDiffValue("Questions")));
        collectMetric.put("Activity|Operations|Total Inserts", utils.getString(utils.getDiffValue("Com_insert")
                + utils.getDiffValue("Com_replace")));
        collectMetric.put("Activity|Operations|Total Queries", utils.getString(utils.getDiffValue("Com_select")
                + utils.getDiffValue("Qcache_hits")));
        collectMetric.put("Activity|Operations|Total Deletes", utils.getString(utils.getDiffValue("Com_delete")));
        collectMetric.put("Activity|Operations|Total Updates", utils.getString(utils.getDiffValue("Com_update")));

        collectMetric.put("Activity|Operations|InnoDB|Total Rows Queried", utils.getString(utils.getDiffValue("innodb_rows_read")));
        collectMetric.put("Activity|Operations|InnoDB|Total Rows Deleted", utils.getString(utils.getDiffValue("innodb_rows_deleted")));
        collectMetric.put("Activity|Operations|InnoDB|Total Rows Updated", utils.getString(utils.getDiffValue("innodb_rows_updated")));
        collectMetric.put("Activity|Operations|InnoDB|Total Rows Inserted", utils.getString(utils.getDiffValue("innodb_rows_inserted")));
        collectMetric.put("Activity|Sort Total", utils.getString(utils.getDiffValue("sort_scan") + utils.getDiffValue("sort_range")));

        // EFFICIENCY
        collectMetric.put("Efficiency|Index|Single Table", utils.getPercent(utils.getDiffValue("select_range"),
                (utils.getDiffValue("select_range") + utils.getDiffValue("select_scan"))));
        collectMetric.put("Efficiency|Index|Joins",
                utils.getPercent(utils.getDiffValue("select_full_join"), (utils.getDiffValue("select_full_join")
                        + utils.getDiffValue("select_range_check") + utils.getDiffValue("select_full_range_join"))));
        collectMetric.put("Efficiency|Sort|Spill to Disk", utils.getPercent(utils.getDiffValue("sort_merge_passes"),
                utils.getDiffValue("sort_scan_sort_range")));
        collectMetric.put("Efficiency|Query|Slow", utils.getPercent(utils.getDiffValue("Slow_queries"), utils.getDiffValue("Com_select")));
        collectMetric.put("Efficiency|Cache|Key Cache|Read Hits", utils.getReversePercent(utils.getDiffValue("key_reads"),
                utils.getDiffValue("key_read_requests")));
        collectMetric.put("Efficiency|Cache|Key Cache|Write Hits", utils.getReversePercent(utils.getDiffValue("key_writes"),
                utils.getDiffValue("key_write_requests")));
        collectMetric.put("Efficiency|Cache|Query Cache|Hits", utils.getPercent(utils.getDiffValue("Qcache_hits"),
                utils.getDiffValue("Com_select")));
        collectMetric.put("Efficiency|InnoDB|Cache|Buffer Pool|Hits", utils.getReversePercent(
                utils.getDiffValue("Innodb_buffer_pool_reads"), utils.getDiffValue("Innodb_buffer_pool_read_requests")));
        collectMetric.put("Efficiency|Cache|Threads|Hits", utils.getReversePercent(utils.getDiffValue("threads_created"),
                utils.getDiffValue("Connections")));
        collectMetric.put("Efficiency|Table Locks|Waited", utils.getPercent(utils.getDiffValue("table_locks_waited"),
                utils.getDiffValue("table_locks_waited") + utils.getDiffValue("table_locks_immediate")));
        collectMetric.put("Efficiency|InnoDB|Row Locks|Waiting", utils.getString(utils.getValue("Innodb_row_lock_current_waits")));
        collectMetric.put("Efficiency|InnoDB|Row Locks|Row Lock Time", utils.getString(utils.getValue("Innodb_row_lock_time")));
        collectMetric.put("Efficiency|InnoDB|Row Locks|Avg Time Waited", utils.getString(utils.getValue("getInnodb_row_lock_time_avg")));
        collectMetric.put("Efficiency|Binary Log|% Transactions too Big", utils.getPercent(utils.getDiffValue("Binlog_cache_disk_use"),
                utils.getDiffValue("Binlog_cache_use")));
        collectMetric.put("Efficiency|Tables|Temp|% Created on Disk", utils.getPercent(utils.getDiffValue("Created_tmp_disk_tables"),
                utils.getDiffValue("Created_tmp_disk_tables") + utils.getDiffValue("Created_tmp_tables")));
        collectMetric.put("Efficiency|InnoDB|Cache|Buffer Pool|% Writes Waited", utils.getPercent(
                utils.getDiffValue("Innodb_buffer_pool_wait_free"), utils.getDiffValue("innodb_buffer_pool_write_requests")));


        collectMetric.put("Resource Utilization|Opened Files", utils.getString("Opened_files"));
        collectMetric.put("Resource Utilization|Slow Queries", utils.getString("Slow_queries"));
        collectMetric.put("Resource Utilization|Max Used Connections", utils.getString("Max_used_connections"));
        collectMetric.put("Resource Utilization|Aborted Connections", utils.getString("Aborted_connects"));
        collectMetric.put("Resource Utilization|Aborted Clients", utils.getString("Aborted_clients"));

        return filterMetricMap(collectMetric,false,null);
    }

    protected List<Metric> collectSlaveMetric(Map<String,String> slaveMetricMap, String slaveName){

        return filterMetricMap(slaveMetricMap,true,slaveName);

    }

    private List<Metric> filterMetricMap(Map<String,String> collectedMetricsMap,boolean slaveMetrics,String slaveName){
        List<Metric> filteredList=Lists.newArrayList();
        String key,value,metricName,prefix;
        if(collectedMetricsMap != null){
            for (Stat s: stat){
                metricName = s.getName();
                if(s.getMetric() != null){
                    for(MetricConfig metrics: s.getMetric()){
                        if(slaveMetrics){
                            key=metricName+SEPARATOR+slaveName+SEPARATOR+metrics.getAttr();
                            value=collectedMetricsMap.get(key);
                            prefix = metricPrefix+metricName+SEPARATOR+slaveName+SEPARATOR+metrics.getAlias();
                        }else{
                            key=metricName+SEPARATOR+metrics.getAttr();
                            value=collectedMetricsMap.get(key);
                            prefix = metricPrefix+metricName+SEPARATOR+metrics.getAlias();
                        }
                        if (!Strings.isNullOrEmpty(value)){
                            Map<String,String> propertiesMap = objectMapper.convertValue(metrics,Map.class);
                            Metric metric = new Metric(key,value,prefix,propertiesMap);
                            filteredList.add(metric);
                        }
                    }
                }
            }
        }else{
            logger.debug("Not collected any metrics. Returning...");
        }
        return filteredList;
    }
}
