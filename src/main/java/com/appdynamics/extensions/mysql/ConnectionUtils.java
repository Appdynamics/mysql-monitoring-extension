package com.appdynamics.extensions.mysql;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Created by abhishek.saxena on 17/06/20.
 */

public class ConnectionUtils {
    private static final Logger logger = ExtensionsLoggerFactory.getLogger(ConnectionUtils.class);

    protected static Connection connect(String host, int port, String user, String plainPassword){

        String connStr = "jdbc:mysql://" + host + ":" + port;
        connStr+="/information_schema?";

        if ((user != null) && (!user.equals(""))){
            connStr+="user="+user;
        }else{
            connStr+="user=root";
        }

        String logConnStr=connStr;

        if((plainPassword != null) && (!plainPassword.equals(""))){
            connStr+="&password="+plainPassword;
            logConnStr+="&password=YES";
        }

        logger.debug("Connecting to: "+logConnStr);

        Connection conn=null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(connStr);
        }catch(ClassNotFoundException e){
            logger.error("Unable to load Driver Class",e);
        } catch (SQLException e){
            logger.error("Unable to create connection",e);
        }

        return conn;

    }
}
