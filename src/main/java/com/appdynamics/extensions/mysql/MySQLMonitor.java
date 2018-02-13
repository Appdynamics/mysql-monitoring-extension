/*
 *   Copyright 2018. AppDynamics LLC and its affiliates.
 *   All Rights Reserved.
 *   This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *   The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */


package com.appdynamics.extensions.mysql;

import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.util.MetricWriteHelper;
import com.appdynamics.extensions.util.MetricWriteHelperFactory;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MySQLMonitor extends AManagedMonitor {

    private static final Logger logger = Logger.getLogger(MySQLMonitor.class);
    private static final String CONFIG_ARG = "config-file";
    private static final String METRIC_PREFIX = "Custom Metrics|MySQL|";

    private Map<String, Map<String, String>> cachedStats;

    private boolean initialized;
    private MonitorConfiguration configuration;

    public MySQLMonitor() {
        System.out.println(logMessage());
        logger.info(logMessage());
        cachedStats = new HashMap<String, Map<String, String>>();
    }

    private String logMessage() {
        return "Using Monitor Version [" + getImplementationVersion() + "]";
    }

    private static String getImplementationVersion() {
        return MySQLMonitor.class.getPackage().getImplementationTitle();
    }


    public TaskOutput execute(Map<String, String> args, TaskExecutionContext taskExecutionContext) throws TaskExecutionException {

        logger.info(logMessage());
        logger.info("Starting the MySQL Monitoring task.");

        Thread thread = Thread.currentThread();
        ClassLoader originalCl = thread.getContextClassLoader();
        thread.setContextClassLoader(AManagedMonitor.class.getClassLoader());

        try {
            if (!initialized) {
                initialize(args);
            }
            configuration.executeTask();

            logger.info("Finished MySQL monitor execution");
            return new TaskOutput("Finished MySQL monitor execution");
        } catch (Exception e) {
            logger.error("Failed to execute the MySQL monitoring task", e);
            throw new TaskExecutionException("Failed to execute the MySQL monitoring task" + e);
        } finally {
            thread.setContextClassLoader(originalCl);
        }
    }

    private void initialize(Map<String, String> argsMap) {
        if (!initialized) {
            final String configFilePath = argsMap.get(CONFIG_ARG);
            MetricWriteHelper metricWriteHelper = MetricWriteHelperFactory.create(this);
            MonitorConfiguration conf = new MonitorConfiguration(METRIC_PREFIX, new TaskRunnable(), metricWriteHelper);
            conf.setConfigYml(configFilePath);
            conf.checkIfInitialized(MonitorConfiguration.ConfItem.CONFIG_YML, MonitorConfiguration.ConfItem.METRIC_PREFIX,
                    MonitorConfiguration.ConfItem.METRIC_WRITE_HELPER, MonitorConfiguration.ConfItem.EXECUTOR_SERVICE);
            this.configuration = conf;
            initialized = true;
        }
    }

    private class TaskRunnable implements Runnable {

        public void run() {

            if (!initialized) {
                logger.info("MySQL Monitor is still initializing");
                return;
            }

            Map<String, ?> config = configuration.getConfigYml();

            List<Map> myServers = (List) config.get("mySQL");
            if (myServers != null && !myServers.isEmpty()) {
                for (Map server : myServers) {

                    MySQLMonitorTask task = new MySQLMonitorTask(server, configuration, cachedStats);
                    configuration.getExecutorService().execute(task);
                }
            } else {
                logger.error("There are no MySQL servers configured");
            }
        }
    }

    public static void main(String[] args) throws TaskExecutionException, InterruptedException {

        ConsoleAppender ca = new ConsoleAppender();
        ca.setWriter(new OutputStreamWriter(System.out));
        ca.setLayout(new PatternLayout("%-5p [%t]: %m%n"));
        logger.getRootLogger().addAppender(ca);


        MySQLMonitor mySQLMonitor = new MySQLMonitor();

        Map<String, String> taskArgs = new HashMap<String, String>();
        taskArgs.put(CONFIG_ARG, "/Users/Muddam/AppDynamics/Code/extensions/mysql-monitoring-extension/src/main/resources/config.yml");


        mySQLMonitor.execute(taskArgs, null);


    }
}
