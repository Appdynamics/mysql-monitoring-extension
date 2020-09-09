package com.appdynamics.extensions.mysql;

import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.mysql.Utility.Constants;
import com.appdynamics.extensions.mysql.config.Stat;
import com.appdynamics.extensions.util.CryptoUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.sql.*;
import java.util.List;
import java.util.Map;

import static com.appdynamics.extensions.mysql.Utility.Constants.SEPARATOR;

public class MySQLMonitorTask implements AMonitorTaskRunnable {


    private MonitorContextConfiguration configuration;
    private MetricWriteHelper metricWriteHelper;
    private Map<String,?> mysqlServer;
    private String metricPrefix;
    private Map<String, Map<String,String>> cachedStats;
    private BigInteger serverHeartbeat = BigInteger.ZERO;

    private MetricCollector metricCollector;


    private static final Logger logger = ExtensionsLoggerFactory.getLogger(MySQLMonitorTask.class);

    public MySQLMonitorTask(MetricWriteHelper metricWriteHelper, MonitorContextConfiguration configuration, Map<String,?> mysqlServer, Map<String,Map<String,String>> cachedStats) {
        this.configuration=configuration;
        this.metricWriteHelper=metricWriteHelper;
        this.mysqlServer=mysqlServer;
        this.metricPrefix=configuration.getMetricPrefix()+SEPARATOR+this.mysqlServer.get(Constants.DISPLAY_NAME)+SEPARATOR;
        this.cachedStats=cachedStats;
    }

    @Override
    public void onTaskComplete() { logger.info("Completed Mysql Monitor task for "+mysqlServer.get(Constants.DISPLAY_NAME)); }

    @Override
    public void run() {

        Stat.Stats stats = (Stat.Stats) configuration.getMetricsXml();
        List<Metric> metricListfiltered=Lists.newArrayList();
        metricCollector = getMetricCollector(stats.getStat(),metricPrefix);
        String name = (String) mysqlServer.get(Constants.DISPLAY_NAME);
        String host = (String)mysqlServer.get(Constants.HOST);
        int port = (Integer) mysqlServer.get(Constants.PORT);
        try {
            Map<String,String> currstats=populate(new String[]{"show global variables","show global status"});

            List<Map> slaves = (List<Map>) mysqlServer.get(Constants.SLAVES);

            if (slaves != null && slaves.size()>0){
                logger.debug("Collecting replication metrics");
                metricListfiltered.addAll(populateReplicationStats("show slave status"));
            }

            Map<String,String> prevStats = cachedStats.get(host+":"+port);

            if(prevStats==null){
                prevStats=Maps.newHashMap();
            }

            metricListfiltered.addAll(metricCollector.collectMetrics(currstats,prevStats));
            cachedStats.put(host + ":" + port, currstats);

        } catch (TaskExecutionException e) {
            logger.error("Error while collecting metrics from ["+name+"]");
        }finally{
            metricListfiltered.add(new Metric(Constants.HEARTBEAT,serverHeartbeat.toString(),metricPrefix+Constants.HEARTBEAT));
            metricWriteHelper.transformAndPrintMetrics(metricListfiltered);
        }
    }


    private Map<String, String> populate(String[] queries) throws TaskExecutionException{
        Connection conn=null;
        Statement stmt=null;

        Map<String,String> statsMap=Maps.newHashMap();
        String name = (String)mysqlServer.get(Constants.DISPLAY_NAME);

        try {
            String host = (String)mysqlServer.get(Constants.HOST);
            int port = (Integer) mysqlServer.get(Constants.PORT);
            String user = (String)mysqlServer.get(Constants.USERNAME);
            String password = (String)mysqlServer.get(Constants.PASSWORD);
            String passwordEncrypted = (String)mysqlServer.get(Constants.PASSWORD_ENCRYPTED);
            String encryptionKey = (String)mysqlServer.get(Constants.ENCRYPTION_KEY);

            String plainPassword = getPassword(password,passwordEncrypted,encryptionKey);

            conn = ConnectionUtils.connect(host,port,user,plainPassword);

            if (conn != null){
                serverHeartbeat = BigInteger.ONE;
            }

            stmt= conn.createStatement();

            for (String query: queries){
                ResultSet rs=null;

                try{
                    if(logger.isDebugEnabled()){
                        logger.debug("Executing query ["+query+"]");
                    }
                    rs = stmt.executeQuery(query);

                    while(rs.next()){
                        String key = rs.getString(1);
                        String value = rs.getString(2);

                        if(logger.isDebugEnabled()){
                            logger.debug("[key,value] = [" + key + "," + value + "]");
                        }
                        statsMap.put(key.toUpperCase(),value);
                    }

                }catch(Exception ex){
                    logger.error("Error while executing query [" + query + "] on [ " + name + "]", ex);
                }finally{
                    closeResultSet(rs);
                }

            }

        } catch (SQLException e) {
            logger.error("Error while creating connection to [" + name + "]", e);
            throw new TaskExecutionException("Error while creating connection to [" + name + "]", e);
        }finally{
            closeStatement(stmt);
            closeConnection(conn);
        }
        return statsMap;
    }

    private List<Metric> populateReplicationStats(String query) {

        Map<String,String> slaveMetricMap = Maps.newHashMap();
        List<Metric> replicationStatsList = Lists.newArrayList();

        List<Map> slaves = (List<Map>) mysqlServer.get(Constants.SLAVES);
        String encryptionKey = (String)mysqlServer.get(Constants.ENCRYPTION_KEY);
        for(Map slave: slaves){
            BigInteger slaveHeartbeat = BigInteger.ZERO;
            Connection conn = null;
            Statement stmt = null;
            String name = (String) slave.get(Constants.DISPLAY_NAME);

            try{
                String host = (String) slave.get(Constants.HOST);
                int port = (Integer) slave.get(Constants.PORT);
                String user = (String) slave.get(Constants.USERNAME);

                String password = (String)slave.get(Constants.PASSWORD);
                String passwordEncrypted = (String)slave.get(Constants.PASSWORD_ENCRYPTED);

                String plainPassword = getPassword(password,passwordEncrypted,encryptionKey);

                conn = ConnectionUtils.connect(host,port,user,plainPassword);
                if (conn != null){
                    slaveHeartbeat = BigInteger.ONE;
                }
                stmt = conn.createStatement();
                ResultSet rs=null;
                try{
                    rs = stmt.executeQuery(query);
                    while(rs.next()){
                        String value = rs.getString("Slave_IO_Running");
                        logger.debug("[key,value] = [Slave_IO_Running," + value + "]");

                        int state=0;
                        if("Yes".equalsIgnoreCase(value)){
                            state=1;
                        }
                        slaveMetricMap.put("Replication" + SEPARATOR + name + SEPARATOR + "Slave IO Running",String.valueOf(state));

                        value = rs.getString("SQL_Delay");
                        slaveMetricMap.put("Replication" + SEPARATOR + name + SEPARATOR + "SQL Delay",value);
                        logger.debug("[key,value] = [SQL Delay," + value + "]");

                        value = rs.getString("Seconds_Behind_Master");
                        slaveMetricMap.put("Replication" + SEPARATOR + name + SEPARATOR + "Seconds Behind Master",value);
                        logger.debug("[key,value] = [Seconds Behind Master," + value + "]");
                    }
                }catch(Exception ex){
                    logger.error("Error while executing query [" + query + "] on slave [ " + name + "]", ex);
                }finally{
                    closeResultSet(rs);
                    replicationStatsList.addAll(metricCollector.collectSlaveMetric(slaveMetricMap,name));
                }

            }catch (TaskExecutionException e) {
                logger.error("Error connecting to slave machine "+name,e);
            } catch (SQLException e) {
                logger.error("Error connecting to slave machine "+name,e);
            } finally{
                closeStatement(stmt);
                closeConnection(conn);
                replicationStatsList.add(new Metric(Constants.HEARTBEAT,slaveHeartbeat.toString(),metricPrefix+"Replication" + SEPARATOR + name + SEPARATOR + Constants.HEARTBEAT));
            }
        }
        return replicationStatsList;
    }

    private String getPassword(String password, String passwordEncrypted, String encryptionKey){
        if (!Strings.isNullOrEmpty(password)){
            return password;
        }
        if (!Strings.isNullOrEmpty(passwordEncrypted)){
            Map<String,String> cryptoMap = Maps.newHashMap();
            cryptoMap.put(Constants.PASSWORD_ENCRYPTED,passwordEncrypted);
            cryptoMap.put(Constants.ENCRYPTION_KEY,encryptionKey);
            logger.debug("Decrypting the encrypted password....");

            return CryptoUtils.getPassword(cryptoMap);
        }

        return "";
    }

    private void closeResultSet(ResultSet rs){
        if (rs==null){
            return;
        }
        try {
            rs.close();
        } catch (SQLException e) {
            logger.error("Unable to close the ResultSet", e);
        }
    }

    private void closeStatement(Statement stmt) {
        if (stmt==null){
            return;
        }
        try {
            stmt.close();
        } catch (SQLException e) {
            logger.error("Unable to close the Statement", e);
        }
    }

    private void closeConnection(Connection conn){
        if (conn==null){
            return;
        }
        try {
            conn.close();
        } catch (SQLException e) {
            logger.error("Unable to close the Connection", e);
        }
    }

    public MetricCollector getMetricCollector(Stat[] stat, String metricPrefix){
        return new MetricCollector(stat,metricPrefix);
    }

}
