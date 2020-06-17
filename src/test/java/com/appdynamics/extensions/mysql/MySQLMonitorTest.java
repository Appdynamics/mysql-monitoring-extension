package com.appdynamics.extensions.mysql;

import com.google.common.collect.Maps;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.junit.Test;

import java.util.Map;

public class MySQLMonitorTest {

    @Test
    public void test() throws TaskExecutionException {
        MySQLMonitor monitor = new MySQLMonitor();
        Map<String,String> taskArgs = Maps.newHashMap();

        taskArgs.put("config-file","src/test/resources/config.yml");
        taskArgs.put("metric-file","src/test/resources/metrics.xml");

        monitor.execute(taskArgs,null);

    }
}
