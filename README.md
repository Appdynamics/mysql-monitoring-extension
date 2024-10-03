AppDynamics MySQL Monitoring Extension
====================================

## Use Case
This extension monitors the MySQL server. This extension should be used with standalone Java Machine Agents.

## Prerequisites

1. Before the extension is installed, the prerequisites mentioned [here](https://community.appdynamics.com/t5/Knowledge-Base/Extensions-Prerequisites-Guide/ta-p/35213) need to be met. Please do not proceed with the extension installation if the specified prerequisites are not met
2. The extension needs to be able to connect to MySQL server in order to collect and send metrics. To do this, you will have to either establish a remote connection in between the extension and the product, or have an agent on the same machine running the product in order for the extension to collect and send the metrics.

## Installation

1. Run "mvn clean install" from "MySqlMonitorRepo"
2. Unzip the contents of MySqlMonitor-\<version\>.zip file (&lt;MySQLRepo&gt; / targets) and copy the directory to `<your-machine-agent-dir>/monitors`.
3. Edit config.yml file and provide the required configuration (see Configuration section)
4. Restart the Machine Agent.

Please place the extension in the **"monitors"** directory of your **Machine Agent** installation directory. Do not place the extension in the **"extensions"** directory of your **Machine Agent** installation directory.

## Configuration

### Config.yml
Please make sure to not use tab (\t) while editing yaml files. You may want to validate the yaml file using a [yaml validator](https://jsonformatter.org/yaml-validator)

#### Configure metric prefix
Please follow section 2.1 of the [Document](https://community.appdynamics.com/t5/Knowledge-Base/How-do-I-troubleshoot-missing-custom-metrics-or-extensions/ta-p/28695) to set up metric prefix.
```
#Metric prefix used when SIM is enabled for your machine agent
#metricPrefix: "Custom Metrics|MySQL|"

#This will publish metrics to specific tier
#Instructions on how to retrieve the Component ID can be found in the Metric Prefix section of https://community.appdynamics.com/t5/Knowledge-Base/How-do-I-troubleshoot-missing-custom-metrics-or-extensions/ta-p/28695
metricPrefix: "Server|Component:<TIER ID>|Custom Metrics|MySQL|"
```

#### MySQL server configuration
Configure the MySQL servers by editing the config.yml file in `<MACHINE_AGENT_HOME>/monitors/MySQLMonitor`.
```
mySQL:
   - name: "Local MySQL"
     host: "localhost"
     port: 3306
     user: "root"
     password: "root"

     #Provide password or passwordEncrypted and encryptionKey
     #encryptedPassword:

     #Slave machines
     slave:
        - name: "Local Slave"
          host: "192.168.0.108"
          port: 3388
          user: "root"
          #password:
           
          #Provide password or passwordEncrypted and encryptionKey
          encryptedPassword: "IGVtC9eudmgG8RDjmRjGPQ=="
          
          
          
encryptionKey: "welcome"
```
- name: Display name for your mysql server which will be displayed in metric path. It should be unique for all servers
- host: Mysql server host
- port: Mysql server host
- user: User which is used to connect to mysql server
- password: Password to connect to mysql server. Provide either password or encryptedPassword and encryptionKey. Refer Credentials encryption section for more details.
- slave: Slave configurations to fetch slave metrics

#### Number of threads
Always include 1 thread per server + 1 to run main task.

#### Yml Validation
Please copy all the contents of the config.yml file and go [here](https://jsonformatter.org/yaml-validator). On reaching the website, paste the contents and press the “Validate YAML” button.

#### monitor.xml
Configure the path to the config.yml and metrics.xml file by editing the <task-arguments> in the monitor.xml file in the `<MACHINE_AGENT_HOME>/monitors/MySQLMonitor/` directory.
Below is the sample
```
<task-arguments>
   <!-- config file-->
   <argument name="config-file" is-required="true" default-value="monitors/MySQLMonitor/config.yml" />
   <argument name="metric-file" is-required="true" default-value="monitors/MySQLMonitor/metrics.xml" />
</task-arguments>
```
On Windows, please specify the absolute path to the config.yml.

Provide path to connector jar in classpath
```
<classpath>mysql-monitoring-extension.jar;/path/to/your/connector.jar</classpath>
```

### Metrics.xml
You can modify metrics of your choice by modifying the provided metrics.xml file. Please look at how the metrics have been defined and follow the same convention when modifying any metrics. You do have the ability to also select your Rollup types for each metric as well as set an alias name that you would like to display on the metric browser.

#### Activity

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

#### Efficiency

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

#### Resource Utilization

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

#### Replication

Replication stats will be available when "slave"'s are configured in the config.yml

|Metric Name            	|
|------------------------------	      |
|Slave IO Running|
|SQL Delay|

## Extensions Workbench
Workbench is an inbuilt feature provided with each extension in order to assist you to fine tune the extension setup before you actually deploy it on the controller. Please review the following document on [How to use the Extensions WorkBench](https://community.appdynamics.com/t5/Knowledge-Base/How-do-I-use-the-Extensions-WorkBench/ta-p/30130).

## Credentials Encryption
Please visit [Encryption Guidelines](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-Password-Encryption-with-Extensions/ta-p/29397) to get detailed instructions on password encryption. The steps in this document will guide you through the whole process.

## Troubleshooting
Please follow the steps listed in this [troubleshooting-document](https://community.appdynamics.com/t5/Knowledge-Base/How-do-I-troubleshoot-missing-custom-metrics-or-extensions/ta-p/28695) in order to troubleshoot your issue. These are a set of common issues that customers might have faced during the installation of the extension.

## Contributing
Always feel free to fork and contribute any changes directly via [GitHub](https://github.com/Appdynamics/mysql-monitoring-extension).

## Version
|          Name            | Version                                                                                         |
|--------------------------|-------------------------------------------------------------------------------------------------|
|Extension Version         | 2.1.1                                                                                           |
|Product Tested On         | 8.0.25                                                                                          |
|Last Update               | 03/10/2024                                                                                      |
|Change List| [ChangeLog](https://github.com/Appdynamics/mysql-monitoring-extension/blob/master/CHANGELOG.md) |
   
**Note**: While extensions are maintained and supported by customers under the open-source licensing model, they interact with agents and Controllers that are subject to [AppDynamics’ maintenance and support policy](https://docs.appdynamics.com/latest/en/product-and-release-announcements/maintenance-support-for-software-versions). Some extensions have been tested with AppDynamics 4.5.13+ artifacts, but you are strongly recommended against using versions that are no longer supported.   
