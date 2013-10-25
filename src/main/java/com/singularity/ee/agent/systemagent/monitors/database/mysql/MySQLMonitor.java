/**
 * Copyright 2013 AppDynamics
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */




package com.singularity.ee.agent.systemagent.monitors.database.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import com.singularity.ee.agent.systemagent.monitors.common.JavaServersMonitor;

public class MySQLMonitor extends JavaServersMonitor
{
	Connection conn = null;
	private volatile String tierName;
	
	public MySQLMonitor()
	{
		oldValueMap = Collections.synchronizedMap(new HashMap<String, String>());
	}
	
	protected void parseArgs(Map<String, String> args)
	{
		super.parseArgs(args);
		tierName = getArg(args, "tier", null); // if the tier is not specified then create the metrics for all tiers
	}

	private Connection connect() throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException
	{
		String connStr = "jdbc:mysql://" + host + ":";
		if ((port == null) || (port.equals("")))
			connStr += "3306";
		else
			connStr += port;

		connStr += "/information_schema?";
		if ((userName != null) && (!userName.equals("")))
			connStr += "user=" + userName;
		else
			connStr += "user=root";

		if ((passwd != null) && (!passwd.equals("")))
			connStr += "&password=" + passwd;

		logger.debug("Connecting to: " + connStr);
		Class.forName("com.mysql.jdbc.Driver").newInstance();
		Connection conn = DriverManager.getConnection(connStr);
		logger.debug("Successfully connected to Mysql DB");

		return conn;
	}

	// collects all monitoring data for this time period from database
	private void populate(Map<String, String> valueMap, String[] queries) throws Exception
	{
		Connection conn = null;
		Statement stmt = null;
		
		boolean debug = logger.isDebugEnabled();
		
		try
		{
			conn = connect();
			stmt = conn.createStatement();
			
			for (String query : queries) 
			{
				ResultSet rs = null;
				
				try 
				{
					if (debug)
					{
						logger.debug("Executing query ["+query+"]");
					}
					
					rs = stmt.executeQuery(query);

					while (rs.next())
					{
						String key = rs.getString(1);
						String value = rs.getString(2);

						if (debug)
						{
							logger.debug("[key,value] = ["+key+","+value+"]");
						}
						
						valueMap.put(key.toUpperCase(), value);
					}
				} 
				catch (Exception ex) 
				{
					logger.error("Error while executing query ["+query+"]", ex);
					throw ex;
				}
				finally
				{
					close(rs, null, null);
				}
			}
			
			// get most accurate time
			currentTime = System.currentTimeMillis();
		}
		finally
		{
			close(null, stmt, conn);
		}
	}

	public TaskOutput execute(Map<String, String> taskArguments, TaskExecutionContext taskContext)
			throws TaskExecutionException
	{
		startExecute(taskArguments, taskContext);

		try
		{
			populate(valueMap, new String[]{"show global variables", "show global status"});
		}
		catch (Exception ex)
		{
			throw new TaskExecutionException(ex);
		}

		// just for debug output
		logger.debug("Starting METRIC COLLECTION for Mysql Monitor.......");

		// RESOURCE UTILIZATION
		printMetric("Resource Utilization|% Connections Used", getPercent("max_used_connections", "max_connections"),
				MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Resource Utilization|Total Active Threads", getString("Threads_running"),
				MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Resource Utilization|Total Cached Threads", getString("Threads_cached"),
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
		printMetric("Resource Utilization|Open Tables", getString("Open_tables"),
				MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Resource Utilization|% Key Cache Used", getPercent(getValue("key_buffer_size")
				- (getValue("key_blocks_unused") * getValue("key_cache_block_size")), getValue("key_buffer_size")),
				MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
		printMetric("Resource Utilization|% Query Cache Used", getPercent(getValue("query_cache_size")
				- getValue("Qcache_free_memory"), getValue("query_cache_size")),
				MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
		printMetric("Resource Utilization|% InnoDB Buffer Pool Used", getPercent(
				getValue("innodb_buffer_pool_pages_total") - getValue("innodb_buffer_pool_pages_free"),
				getValue("innodb_buffer_pool_pages_total")), MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
				MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
		printMetric("Resource Utilization|Log File % of InnoDB Buffer Pool", getPercent(
				getValue("Innodb_log_file_size") * getValue("Innodb_log_files_in_group"),
				getValue("innodb_buffer_pool_pages_total") * (2 * 8192)),
				MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);

		// ACTIVITY
		printMetric("Activity|Connections|Total Attempts", getString(getDiffValue("Connections")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|Connections|Total Failed+Aborted", getString(getDiffValue("aborted_clients")
				+ getDiffValue("aborted_connects")), MetricWriter.METRIC_AGGREGATION_TYPE_SUM,
				MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric(
				"Activity|Transactions|Total", // this doesn't count auto-commit
												// ones!
				getString(getDiffValue("Com_rollback") + getDiffValue("Com_commit")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|Bytes between Client-Server|Sent", getString(getDiffValue("bytes_sent")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|Bytes between Client-Server|Received", getString(getDiffValue("Bytes_received")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|InnoDB|Bytes Read", getString(getDiffValue("Innodb_data_read")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|InnoDB|Bytes Written", getString(getDiffValue("Innodb_data_written")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|InnoDB|File Synchronization", getString(getDiffValue("Innodb_data_fsyncs")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|Tables|Opened", getString(getDiffValue("Opened_tables")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|Tables|Temp Created", getString(getDiffValue("Created_tmp_disk_tables")
				+ getDiffValue("Created_tmp_tables")), MetricWriter.METRIC_AGGREGATION_TYPE_SUM,
				MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|Transactions|Rolled Back", getString(getDiffValue("Com_rollback")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|Transactions|Committed", getString(getDiffValue("Com_commit")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		
		printMetric("Activity|Operations|Total Requests", getString(getDiffValue("Questions")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|Operations|Total Inserts", getString(getDiffValue("Com_insert")
				+ getDiffValue("Com_replace")), MetricWriter.METRIC_AGGREGATION_TYPE_SUM,
				MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|Operations|Total Queries", getString(getDiffValue("Com_select")
				+ getDiffValue("Qcache_hits")), MetricWriter.METRIC_AGGREGATION_TYPE_SUM,
				MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|Operations|Total Deletes", getString(getDiffValue("Com_delete")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|Operations|Total Updates", getString(getDiffValue("Com_update")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		
		printMetric("Activity|Operations|InnoDB|Total Rows Queried", getString(getDiffValue("innodb_rows_read")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|Operations|InnoDB|Total Rows Deleted", getString(getDiffValue("innodb_rows_deleted")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|Operations|InnoDB|Total Rows Updated", getString(getDiffValue("innodb_rows_updated")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|Operations|InnoDB|Total Rows Inserted", getString(getDiffValue("innodb_rows_inserted")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Activity|Sort Total", getString(getDiffValue("sort_scan") + getDiffValue("sort_range")),
				MetricWriter.METRIC_AGGREGATION_TYPE_SUM, MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);

		// EFFICIENCY
		printMetric("Efficiency|Index|Single Table", getPercent(getDiffValue("select_range"),
				(getDiffValue("select_range") + getDiffValue("select_scan"))),
				MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE, MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
		printMetric("Efficiency|Index|Joins",
				getPercent(getDiffValue("select_full_join"), (getDiffValue("select_full_join")
						+ getDiffValue("select_range_check") + getDiffValue("select_full_range_join"))),
				MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE, MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
		printMetric("Efficiency|Sort|Spill to Disk", getPercent(getDiffValue("sort_merge_passes"),
				getDiffValue("sort_scan_sort_range")), MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE,
				MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
		printMetric("Efficiency|Query|Slow", getPercent(getDiffValue("Slow_queries"), getDiffValue("Com_select")),
				MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE, MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
		printMetric("Efficiency|Cache|Key Cache|Read Hits", getReversePercent(getDiffValue("key_reads"),
				getDiffValue("key_read_requests")), MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE,
				MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
		printMetric("Efficiency|Cache|Key Cache|Write Hits", getReversePercent(getDiffValue("key_writes"),
				getDiffValue("key_write_requests")), MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE,
				MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
		printMetric("Efficiency|Cache|Query Cache|Hits", getPercent(getDiffValue("Qcache_hits"),
				getDiffValue("Com_select")), MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE,
				MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
		printMetric("Efficiency|InnoDB|Cache|Buffer Pool|Hits", getReversePercent(
				getDiffValue("Innodb_buffer_pool_reads"), getDiffValue("Innodb_buffer_pool_read_requests")),
				MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE, MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
		printMetric("Efficiency|Cache|Threads|Hits", getReversePercent(getDiffValue("threads_created"),
				getDiffValue("Connections")), MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE,
				MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
		printMetric("Efficiency|Table Locks|Waited", getPercent(getDiffValue("table_locks_waited"),
				getDiffValue("table_locks_waited") + getDiffValue("table_locks_immediate")),
				MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE, MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
		printMetric("Efficiency|InnoDB|Row Locks|Waiting", getString(getValue("Innodb_row_lock_current_waits")),
				MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		printMetric("Efficiency|InnoDB|Row Locks|Avg Time Waited", getString(getValue("getInnodb_row_lock_time_avg")),
				MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
		printMetric("Efficiency|Binary Log|% Transactions too Big", getPercent(getDiffValue("Binlog_cache_disk_use"),
				getDiffValue("Binlog_cache_use")), MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE,
				MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
		printMetric("Efficiency|Tables|Temp|% Created on Disk", getPercent(getDiffValue("Created_tmp_disk_tables"),
				getDiffValue("Created_tmp_disk_tables") + getDiffValue("Created_tmp_tables")),
				MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE, MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);
		printMetric("Efficiency|InnoDB|Cache|Buffer Pool|% Writes Waited", getPercent(
				getDiffValue("Innodb_buffer_pool_wait_free"), getDiffValue("innodb_buffer_pool_write_requests")),
				MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE, MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);

		return this.finishExecute();
	}

	protected String getMetricPrefix()
	{
		if (tierName != null)
		{
			return "Server|Component:"+tierName+"|MySQL Server|";
		}
		else
		{	
			return "Custom Metrics|MySQL Server|";
		}
	}
	
}
