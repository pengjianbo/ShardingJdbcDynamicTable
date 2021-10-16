# ShardingJDBC动态分表实现,支持按年/月/周/日分表

随着业务的不断发展单表的数据会越来越大，对于CURD操作效率变慢和数据库压力增大，此时需要考虑分表分库的方法来解决该问题。假设我们有一种场景，每天有近100万的数据要落到表中，这样一个表中的数据很容易就到了1000万（一般在业务开中单张表的数据尽量不要超过1000万），因此我们需要以每天为单位进行分表；但不可能一次性创建所有的日期的表，在ShardingJDBC中本身是不支持根据时间动态进行分表的。本文将要通过`反射`+`定时任务`方式实现动态分表。

**1.新建定时任务**

定时任务的作用：
①通过反射使ShardingJDBC支持动态分区
②每天23点提前创建第二天的分区表
③在这里还可以将过去或未来的表进行创建

```java
@Component
@Slf4j
public class ShardingTableRuleActualTablesRefreshScheduler implements InitializingBean {

    @Autowired
    private DataSource dataSource;

    @Override
    public void afterPropertiesSet() throws SQLException {
        actualTablesRefresh();
    }

    @Scheduled(cron = "0 0 23 * * ?")
    public void actualTablesRefresh() throws SQLException {
        ShardingDataSource dataSource = (ShardingDataSource) this.dataSource;
        ShardingRule shardingRule = dataSource.getRuntimeContext().getRule();

        Map<String, String> dynamicTables = getDynamicTables(shardingRule);

        for (String logicTable : dynamicTables.keySet()) {
            TableRule tableRule = shardingRule.getTableRule(logicTable);
            if (tableRule == null) {
                log.error("不存在的逻辑表:{}", logicTable);
                continue;
            }

            log.info("正在对{}进行动态分表", logicTable);
            List<DataNode> dataNodes = tableRule.getActualDataNodes();
            if (CollectionUtils.isEmpty(dataNodes)) {
                log.error("{}缺少原始表", logicTable);
                continue;
            }

            //分区表集合
            Set<String> tableNames = dataNodes.stream()
                    .map(DataNode::getTableName)
                    .collect(Collectors.toSet());
            String newTable = getNextDynamicTable(logicTable, dynamicTables.get(logicTable));
            tableNames.add(newTable);

            //修改ShardingJDBC中的变量，得到动态分区的效果
            modifyShardingDatasourceToTablesMap(tableNames, tableRule, dataNodes.get(0));
            modifyShardingDataNodeIndexMap(tableNames, tableRule, dataNodes);
        }

    }

    /**
     * 修改ShardingJDBC内存中的分区表信息
     *
     * @param tableNames 分区表集合
     * @param tableRule  规则
     * @param dataNode   某个分区表的dataNode
     * @throws SQLException
     */
    private void modifyShardingDatasourceToTablesMap(Set<String> tableNames, TableRule tableRule, DataNode dataNode) throws SQLException {
        String dataSourceName = dataNode.getDataSourceName();
        Map<String, Collection<String>> datasourceToTablesMap = tableRule.getDatasourceToTablesMap();
        Collection<String> actualDatasourceNames = tableRule.getActualDatasourceNames();
        for (String tableName : tableNames) {
            if (StringUtils.equals(tableName, dataNode.getTableName())) {
                continue;
            }

            String fullTableName = String.format("%s.%s", dataSourceName, tableName);
            log.info("添加表:{}到Sharding中", fullTableName);
            createTable(tableName, dataNode.getTableName());

            //将分区表加入到Sharding中
            Collection<String> tableList = datasourceToTablesMap.get(dataSourceName);
            if (tableList == null) {
                tableList = new ArrayList<>();
            }
            if (!tableList.contains(tableName)) {
                tableList.add(tableName);
            }
            datasourceToTablesMap.put(dataSourceName, tableList);
            if (!actualDatasourceNames.contains(dataSourceName)) {
                actualDatasourceNames.add(dataSourceName);
            }
        }
    }

    /**
     * 修改ShardingJDBC内存中的dataNodeIndexMap变量
     *
     * @param tableNames 分区表集合
     * @param tableRule  规则
     * @param dataNodes
     */
    private void modifyShardingDataNodeIndexMap(Set<String> tableNames, TableRule tableRule, List<DataNode> dataNodes) {
        try {
            //添加新的分区表到dataNodes中
            String dataSourceName = dataNodes.get(0).getDataSourceName();
            for (String tableName : tableNames) {
                String fullTableName = String.format("%s.%s", dataSourceName, tableName);
                DataNode newDataNode = new DataNode(fullTableName);
                if (!dataNodes.contains(newDataNode)) {
                    dataNodes.add(newDataNode);
                }
            }

            Field dataNodeIndexMapField = TableRule.class.getDeclaredField("dataNodeIndexMap");
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(dataNodeIndexMapField, dataNodeIndexMapField.getModifiers() & ~Modifier.FINAL);
            dataNodeIndexMapField.setAccessible(true);
            int index = 0;
            Map<DataNode, Integer> dataNodeIndexMap = new HashMap<>();
            for (Iterator<DataNode> it = dataNodes.iterator(); it.hasNext(); ++index) {
                DataNode dataNode = it.next();
                dataNodeIndexMap.put(dataNode, index);
            }
            dataNodeIndexMapField.set(tableRule, dataNodeIndexMap);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("修改dataNodeIndexMap异常", e);
        }
    }

    /**
     * 获取下一个日期的分区表
     *
     * @param logicTable 逻辑表
     * @param dateFormat 日期格式
     * @return 下一个分区表名
     */
    private String getNextDynamicTable(String logicTable, String dateFormat) {
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(new Date());
        calendar.add(Calendar.DATE, 1);
        return String.format("%s_%s", logicTable, sdf.format(calendar.getTime()));
    }

    /**
     * 获取需要动态分区的表
     *
     * @param shardingRule <逻辑表, 日期格式>
     * @return 需要动态分区的逻辑表
     */
    private Map<String, String> getDynamicTables(ShardingRule shardingRule) {
        Map<String, String> results = new HashMap<>();
        ShardingRuleConfiguration configuration = shardingRule.getRuleConfiguration();

        for (TableRuleConfiguration ruleConfig : configuration.getTableRuleConfigs()) {
            if (!(ruleConfig.getTableShardingStrategyConfig() instanceof StandardShardingStrategyConfiguration)) {
                continue;
            }
            StandardShardingStrategyConfiguration cfg = (StandardShardingStrategyConfiguration) ruleConfig.getTableShardingStrategyConfig();

            if (cfg.getPreciseShardingAlgorithm() instanceof DayDynamicStandardShardingAlgorithm
                    || cfg.getPreciseShardingAlgorithm() instanceof WeekDynamicStandardShardingAlgorithm
                    || cfg.getPreciseShardingAlgorithm() instanceof MonthDynamicStandardShardingAlgorithm
                    || cfg.getPreciseShardingAlgorithm() instanceof YearDynamicStandardShardingAlgorithm) {

                DynamicStandardShardingAlgorithm algorithm = (DynamicStandardShardingAlgorithm) cfg.getPreciseShardingAlgorithm();
                results.put(ruleConfig.getLogicTable(), algorithm.getDateFormat());

            }
        }

        return results;
    }

    /**
     * 创建表
     *
     * @param newTable    新表
     * @param beforeTable 已经存在的分区表
     * @throws SQLException
     */
    private void createTable(String newTable, String beforeTable) throws SQLException {
        String sql = String.format("CREATE TABLE IF NOT EXISTS %s LIKE %s", newTable, beforeTable);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}

```

**2.分区策略实现**

分区策略是对`范围（Range）`和`精准(Precise)`算法进行实现，主要是为了定位数据的存放在哪些分区表中。

```java
@Slf4j
public class DynamicStandardShardingAlgorithm implements PreciseShardingAlgorithm<Date>, RangeShardingAlgorithm<Date> {

    final static long ONE_DAY_MILLISECOND = 86400000L;

    final static String DAY_DATE_FORMAT = "yyyyMMdd";
    final static String WEEK_DATE_FORMAT = "yyyyw";
    final static String MONTH_DATE_FORMAT = "yyyyMM";
    final static String YEAR_DATE_FORMAT = "yyyy";

    private final String dateFormat;

    public DynamicStandardShardingAlgorithm(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    @Override
    public String doSharding(Collection<String> collection, PreciseShardingValue<Date> preciseShardingValue) {
        Set<String> dateLists = generateTableNames(preciseShardingValue.getLogicTableName(), dateFormat,
                preciseShardingValue.getValue(), preciseShardingValue.getValue());
        if (dateLists.isEmpty()) {
            return null;
        }

        log.debug("ShardingJDBC精准查找【{}】", StringUtils.join(dateLists, ","));
        return dateLists.iterator().next();
    }

    @Override
    public Collection<String> doSharding(Collection<String> collection, RangeShardingValue<Date> rangeShardingValue) {
        Collection<String> result = new LinkedHashSet<>();
        Range<Date> shardingKey = rangeShardingValue.getValueRange();
        String logicTableName = rangeShardingValue.getLogicTableName();
        Date startTime = shardingKey.lowerEndpoint();
        Date endTime = shardingKey.upperEndpoint();
        Date now = new Date();
        if (startTime.after(now)) {
            startTime = now;
        }
        if (endTime.after(now)) {
            endTime = now;
        }
        Collection<String> tables = generateTableNames(logicTableName, dateFormat, startTime, endTime);
        if (CollectionUtils.isNotEmpty(tables)) {
            log.debug("ShardingJDBC区间查找【{}】", StringUtils.join(tables, ","));
            result.addAll(tables);
        }
        return result;
    }

    Set<String> generateTableNames(String logicTableName, String dateFormat, Date startDate, Date endDate) {
        long endStamp = endDate.getTime();
        long rollStamp = startDate.getTime();
        Set<String> list = new HashSet<>();
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        while (endStamp >= rollStamp) {
            String suffix = sdf.format(new Date(rollStamp));
            list.add(String.format("%s_%s", logicTableName, suffix));
            rollStamp = rollStamp + ONE_DAY_MILLISECOND;
        }

        return list;
    }

    public String getDateFormat() {
        return dateFormat;
    }
}
```

**3.按天分区**

```java
public class DayDynamicStandardShardingAlgorithm extends DynamicStandardShardingAlgorithm {

    public DayDynamicStandardShardingAlgorithm() {
        super(DAY_DATE_FORMAT);
    }
}
```

**4.按周分区**

```java
public class WeekDynamicStandardShardingAlgorithm extends DynamicStandardShardingAlgorithm {

    public WeekDynamicStandardShardingAlgorithm() {
        super(WEEK_DATE_FORMAT);
    }
}
```

**5.按月分区**

```java
public class MonthDynamicStandardShardingAlgorithm extends DynamicStandardShardingAlgorithm {

    public MonthDynamicStandardShardingAlgorithm() {
        super(MONTH_DATE_FORMAT);
    }
}
```

**6.按年分区**

```java
public class YearDynamicStandardShardingAlgorithm extends DynamicStandardShardingAlgorithm {

    public YearDynamicStandardShardingAlgorithm() {
        super(YEAR_DATE_FORMAT);
    }
}
```

**7.配置**

```properties
# 分表策略
spring.shardingsphere.sharding.tables.day_partition_table.actual-data-nodes=ds0.day_partition_table_$->{20210101..20210111}
# 指定分区字段
spring.shardingsphere.sharding.tables.day_partition_table.table-strategy.standard.sharding-column=partitionDate
# 自定义分表算法
spring.shardingsphere.sharding.tables.day_partition_table.table-strategy.standard.precise-algorithm-class-name=cc.bbmax.shardingjdbc.dynamictable.sharding.DayDynamicStandardShardingAlgorithm
spring.shardingsphere.sharding.tables.day_partition_table.table-strategy.standard.range-algorithm-class-name=cc.bbmax.shardingjdbc.dynamictable.sharding.DayDynamicStandardShardingAlgorithm
```


