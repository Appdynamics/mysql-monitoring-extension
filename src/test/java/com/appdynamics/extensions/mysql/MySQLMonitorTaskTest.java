package com.appdynamics.extensions.mysql;

import com.appdynamics.extensions.AMonitorJob;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.mysql.config.Stat;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RunWith(PowerMockRunner.class)
public class MySQLMonitorTaskTest {

    MetricWriteHelper metricWriteHelper = Mockito.mock(MetricWriteHelper.class);

    private String metricPrefix = "Custom Metrics|MySQL|";

    MonitorContextConfiguration contextConfiguration = new MonitorContextConfiguration("MySQL Monitor","Custom Metrics|MySQL|",Mockito.mock(File.class),Mockito.mock(AMonitorJob.class));

    private Map<String,?> config;
    private Map server;

    private Map<String,Map<String,String>> cachedstats = new HashMap<>();

    private MetricCollector metricCollector;

    private Stat.Stats stats;
    private Stat[] stat;


    @Before
    public void init(){
        contextConfiguration.setConfigYml("src/test/resources/config.yml");
        contextConfiguration.setMetricXml("src/test/resources/metrics.xml", Stat.Stats.class);

        stats = (Stat.Stats) contextConfiguration.getMetricsXml();
        stat = stats.getStat();

        config = contextConfiguration.getConfigYml();

    }

    @Test
    public void testRunFunctionality() {

        List<String> resultList = Lists.newArrayList();

        ArgumentCaptor<List> pathCaptor = ArgumentCaptor.forClass(List.class);

        server = ((List<Map<String, ?>>) config.get("mySQL")).get(0);

        MySQLMonitorTask spytask = Mockito.spy(new MySQLMonitorTask(metricWriteHelper,contextConfiguration,server,cachedstats));

        Mockito.doReturn(metricCollector).when(spytask).getMetricCollector(stat,metricPrefix);

        spytask.run();

        Mockito.verify(metricWriteHelper).transformAndPrintMetrics(pathCaptor.capture());

        //Resource utilization
        resultList.add("Resource Utilization|% Connections Used");
        resultList.add("Resource Utilization|Total Active Threads");
        resultList.add("Resource Utilization|Total Cached Threads");
        resultList.add("Resource Utilization|Total Connected Threads");
        resultList.add("Resource Utilization|Open Tables");
        resultList.add("Resource Utilization|% Key Cache Used");
        resultList.add("Resource Utilization|% Query Cache Used");
        resultList.add("Resource Utilization|% InnoDB Buffer Pool Used");
        resultList.add("Resource Utilization|Log File % of InnoDB Buffer Pool");
        resultList.add("Resource Utilization|Opened Files");
        resultList.add("Resource Utilization|Slow Queries");
        resultList.add("Resource Utilization|Max Used Connections");
        resultList.add("Resource Utilization|Aborted Connections");
        resultList.add("Resource Utilization|Aborted Clients");

        //activity
        resultList.add("Activity|Connections|Total Attempts");
        resultList.add("Activity|Connections|Total Failed+Aborted");
        resultList.add("Activity|Transactions|Total");
        resultList.add("Activity|Bytes between Client-Server|Sent");
        resultList.add("Activity|Bytes between Client-Server|Received");
        resultList.add("Activity|InnoDB|Bytes Read");
        resultList.add("Activity|InnoDB|Bytes Written");
        resultList.add("Activity|InnoDB|File Synchronization");
        resultList.add("Activity|Tables|Opened");
        resultList.add("Activity|Tables|Temp Created");
        resultList.add("Activity|Transactions|Rolled Back");
        resultList.add("Activity|Transactions|Committed");
        resultList.add("Activity|Operations|Total Requests");
        resultList.add("Activity|Operations|Total Inserts");
        resultList.add("Activity|Operations|Total Queries");
        resultList.add("Activity|Operations|Total Deletes");
        resultList.add("Activity|Operations|Total Updates");
        resultList.add("Activity|Operations|InnoDB|Total Rows Queried");
        resultList.add("Activity|Operations|InnoDB|Total Rows Deleted");
        resultList.add("Activity|Operations|InnoDB|Total Rows Updated");
        resultList.add("Activity|Operations|InnoDB|Total Rows Inserted");
        resultList.add("Activity|Sort Total");

        //Efficiency
        resultList.add("Efficiency|Index|Single Table");
        resultList.add("Efficiency|Index|Joins");
        resultList.add("Efficiency|Sort|Spill to Disk");
        resultList.add("Efficiency|Query|Slow");
        resultList.add("Efficiency|Cache|Key Cache|Read Hits");
        resultList.add("Efficiency|Cache|Key Cache|Write Hits");
        resultList.add("Efficiency|Cache|Query Cache|Hits");
        resultList.add("Efficiency|InnoDB|Cache|Buffer Pool|Hits");
        resultList.add("Efficiency|Cache|Threads|Hits");
        resultList.add("Efficiency|Table Locks|Waited");
        resultList.add("Efficiency|InnoDB|Row Locks|Waiting");
        resultList.add("Efficiency|InnoDB|Row Locks|Row Lock Time");
        resultList.add("Efficiency|InnoDB|Row Locks|Avg Time Waited");
        resultList.add("Efficiency|Binary Log|% Transactions too Big");
        resultList.add("Efficiency|Tables|Temp|% Created on Disk");
        resultList.add("Efficiency|InnoDB|Cache|Buffer Pool|% Writes Waited");
        resultList.add("HeartBeat");

        for(Metric metric: (List<Metric>)pathCaptor.getValue()){
            Assert.assertTrue(resultList.contains(metric.getMetricName()));
        }

    }

}
