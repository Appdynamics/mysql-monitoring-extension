mysql-monitoring-extension
==========================

##Use Case

MySQL is an open source relational database management system.

The MySQL custom monitor captures metrics from a MySQL server and displays them in the AppDynamics Metric Browser. It interacts with the API's provided by MySQL to return the following metrics:

- Availability: Percentage of the time the database is up, and a graph of up/down over time.
- Resource Utilization: Database resources currently in use versus what is available including connections, threads, and cache memory.
- Activity: Database throughput over the last minute, including requests, transactions, tables, rows, and bytes.
- Efficiency: Information about database optimization to maximize throughput, such as caches and indexes.

The MySQL custom monitor also lists the following:

- Top Queries: The queries that take the most time during a certain interval of time.
- Top Activity: What internal operations take the most time over a certain interval of time.

##Installation

1. Run 'ant package' from the mysql-monitoring-extension directory
2. Deploy the file MySQLMonitor.zip found in the 'dist' directory into \<machineagent install dir\>/monitors/
3. Unzip the deployed file
4. Open \<machineagent install dir\>/monitors/MySQLMonitor/monitor.xml and configure the MySQL credentials
5. Restart the machineagent
6. In the AppDynamics Metric Browser, look for: Application Infrastructure Performance | \<Tier\> | MySQL Server

##Contributing

Always feel free to fork and contribute any changes directly via [GitHub](https://github.com/Appdynamics/mysql-monitoring-extension).

##Community

Find out more in the [AppSphere](http://appsphere.appdynamics.com/t5/Extensions/MySQL-Database-Monitoring-Extension/idi-p/755) community.

##Support

For any questions or feature request, please contact [AppDynamics Center of Excellence](mailto:ace-request@appdynamics.com).
