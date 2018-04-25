AppDynamics MySQL Monitoring Extension
====================================

## Introduction ##

This extension monitors the MySQL server. This extension should be used with standalone Java Machine Agents.


## Installation ##

1. To build from the source, run "mvn clean install" and find the MySQLMonitor.zip file in the "target" folder.
   You can also download the MySQLMonitor.zip from [AppDynamics Exchange][].
2. Unzip as "MySQLMonitor" and copy the "MySQLMonitor" directory to `<MACHINE_AGENT_HOME>/monitors`.

## Configuration ##

###Note
Please make sure to not use tab (\t) while editing yaml files. You may want to validate the yaml file using a yaml validator http://yamllint.com/

1. Configure the MySQL servers by editing the config.yml file in `<MACHINE_AGENT_HOME>/monitors/MySQLMonitor`.

     ```
      mySQL:
          - name: "Local MySQL"
            host: "localhost"
            port: 3388
            user: "root"
            #Provide password or passwordEncrypted and encryptionKey
            password: "root"

            passwordEncrypted:
            encryptionKey:

            #Slave machines
            slave:
               - name: "Local Slave"
                 host: "192.168.0.108"
                 port: 3306
                 user: "root"
                 #Provide password or passwordEncrypted and encryptionKey
                 password:

                 passwordEncrypted: "IGVtC9eudmgG8RDjmRjGPQ=="
                 encryptionKey: "welcome"


      #timeout for the metric collector thread
      threadTimeout: 30

      # number of concurrent tasks
      numberOfThreads: 5

      #prefix used to show up metrics in AppDynamics
      metricPrefix:  "Custom Metrics|MySQL|"
    ```


2. Configure the path to the config.yaml file by editing the <task-arguments> in the monitor.xml file in the `<MACHINE_AGENT_HOME>/monitors/MySQLMonitor/` directory.
Below is the sample

    ```

         <task-arguments>
            <!-- config file-->
            <argument name="config-file" is-required="true" default-value="monitors/MySQLMonitor/config.yml" />
         </task-arguments>

    ```

    On Windows, please specify the absolute path to the config.yml.

##Password Encryption Support
To avoid setting the clear text password in the config.yml, please follow the process below to encrypt the password. This process requires the Oracle Java class sun/misc/BASE64Encode which is not present in all Java implementations. You may need to download a suitable Java runtime from [www.java.com](https://www.java.com/).

1. Download the util jar to encrypt the password from [https://github.com/Appdynamics/maven-repo/blob/master/releases/com/appdynamics/appd-exts-commons/1.1.2/appd-exts-commons-1.1.2.jar](https://github.com/Appdynamics/maven-repo/blob/master/releases/com/appdynamics/appd-exts-commons/1.1.2/appd-exts-commons-1.1.2.jar) and navigate to the downloaded directory
2. Encrypt password from the commandline
`java -cp appd-exts-commons-1.1.2.jar com.appdynamics.extensions.crypto.Encryptor encryptionKey myPassword`
3. Specify the passwordEncrypted and encryptionKey in config.yml

## Metrics

In metric browser metrics will be displayed in [Custom Metrics|MySQL|

###Activity

|Metric Name            	|
|------------------------------	      |
|Bytes between Client-Server/Sent     |
|Bytes between Client-Server/Received |
|Connections/Total Attempts|
|Connections/Total Failed+Aborted|
|InnoDB/Bytes Read|
|InnoDB/Bytes Written|
|InnoDB/File Synchronization|
|Operations/InnoDB/Total Rows Queried|
|Operations/InnoDB/Total Rows Deleted|
|Operations/InnoDB/Total Rows Updated|
|Operations/InnoDB/Total Rows Inserted|
|Operations/Total Requests|
|Operations/Total Inserts|
|Operations/Total Queries|
|Operations/Total Deletes|
|Operations/Total Updates|
|Tables/Opened|
|Tables/Temp Created|
|Transactions/Total|
|Transactions/Rolled Back|
|Transactions/Committed|
|Sort Total|

###Efficiency

|Metric Name            	|
|------------------------------	      |
|Index/Single Table|
|Index/Joins|
|Sort/Spill to Disk|
|Query/Slow|
|Cache/Key Cache/Read Hits|
|Cache/Key Cache/Write Hits|
|Cache/Query Cache/Hits|
|Cache/Threads/Hits|
|InnoDB/Cache/Buffer Pool/Hits|
|InnoDB/Cache/Buffer Pool/% Writes Waited|
|InnoDB/Row Locks/Waiting|
|InnoDB/Row Locks/Row Lock Time|
|InnoDB/Row Locks/Avg Time Waited|
|Table Locks/Waited|
|Binary Log/% Transactions too Big|
|Tables/Temp/% Created on Disk|

###Resource Utilization

|Metric Name            	|
|------------------------------	      |
|% Connections Used|
|Total Active Threads|
|Total Cached Threads|
|Total Connected Threads|
|Open Tables|
|% Key Cache Used|
|% Query Cache Used|
|% InnoDB Buffer Pool Used|
|Log File % of InnoDB Buffer Pool|
|Opened Files|
|Slow Queries|
|Max Used Connections|
|Aborted Connections|
|Aborted Clients|

###Replication

Replication stats will be available when "slave"'s are configured in the config.yml

|Metric Name            	|
|------------------------------	      |
|Slave IO Running|
|SQL Delay|

## Custom Dashboard ##
![](https://github.com/Appdynamics/mysql-monitoring-extension/blob/master/MySQL.png)

## Troubleshooting ##

1. Verify Machine Agent Data: Please start the Machine Agent without the extension and make sure that it reports data.
   Verify that the machine agent status is UP and it is reporting Hardware Metrics.

2. config.yml:Validate the file here. http://www.yamllint.com/

3. The config cannot be null :
   This usually happens when on a windows machine in monitor.xml you give config.yaml file path with linux file path separator `/`.
   Use Windows file path separator `\` e.g. `monitors\MySQLMonitor\config.yaml`. On Windows, please specify absolute file path.

4. Metric Limit: Please start the machine agent with the argument -Dappdynamics.agent.maxMetrics=5000 if there is a metric limit reached
   error in the logs. If you don't see the expected metrics, this could be the cause.

5. Debug Logs:Edit the file, /conf/logging/log4j.xml and update the level of the appender com.appdynamics to debug .
   Let it run for 5-10 minutes and attach the logs to a support ticket

## Contributing ##

Always feel free to fork and contribute any changes directly via [GitHub][].

## Community ##

Find out more in the [AppDynamics Exchange][].

## Support ##

For any questions or feature request, please contact [AppDynamics Center of Excellence][].

**Version:** 2.0.0
**Controller Compatibility:** 3.7+


[Github]: https://github.com/Appdynamics/mysql-monitoring-extension
[AppDynamics Exchange]: https://www.appdynamics.com/community/exchange/extension/mysql-database-monitoring-extension/
[AppDynamics Center of Excellence]: mailto:help@appdynamics.com

