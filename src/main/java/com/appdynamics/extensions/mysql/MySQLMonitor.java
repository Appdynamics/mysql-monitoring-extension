package com.appdynamics.extensions.mysql;

import com.appdynamics.extensions.ABaseMonitor;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.mysql.config.Stat;
import com.appdynamics.extensions.util.AssertUtils;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.appdynamics.extensions.mysql.Utility.Constants.DEFAULT_METRIC_PREFIX;

public class MySQLMonitor extends ABaseMonitor {

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(MySQLMonitor.class);

    private static final String METRIC_PREFIX = DEFAULT_METRIC_PREFIX;
    private Map<String,?> configYml = Maps.newHashMap();
    private MonitorContextConfiguration monitorContextConfiguration;
    private Map<String, Map<String,String>> cachedStats;

    @Override
    protected String getDefaultMetricPrefix() {
        return METRIC_PREFIX;
    }

    @Override
    public String getMonitorName() {
        return "Mysql Monitor";
    }

    @Override
    protected void initializeMoreStuff(Map<String, String> args) {
        //super.initializeMoreStuff(args);
        monitorContextConfiguration = getContextConfiguration();
        configYml = monitorContextConfiguration.getConfigYml();
        AssertUtils.assertNotNull(configYml,"The config.yml is not available");
        cachedStats=new HashMap<>();
        monitorContextConfiguration.setMetricXml(args.get("metric-file"), Stat.Stats.class);
    }

    @Override
    protected void doRun(TasksExecutionServiceProvider tasksExecutionServiceProvider) {

        List<Map<String,?>> mysqlServers =  (List<Map<String,?>>)configYml.get("mySQL");

        for (Map<String,?> mysqlserver: mysqlServers){
            AssertUtils.assertNotNull(mysqlserver,"The server arguments cannot be empty ");
            AssertUtils.assertNotNull(mysqlserver.get("name"),"The name cannot be null");
            logger.info("Starting monitoring task for server "+mysqlserver.get("name"));
            MySQLMonitorTask task = new MySQLMonitorTask(tasksExecutionServiceProvider.getMetricWriteHelper(), monitorContextConfiguration,mysqlserver,cachedStats);
            tasksExecutionServiceProvider.submit((String)mysqlserver.get("name"),task);
        }
    }

    @Override
    protected List<Map<String, ?>> getServers() {
        return (List<Map<String,?>>)configYml.get("mySQL");
    }
}
