package cc.bbmax.shardingjdbc.dynamictable.sharding.scheduler;

import cc.bbmax.shardingjdbc.dynamictable.sharding.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shardingsphere.api.config.sharding.ShardingRuleConfiguration;
import org.apache.shardingsphere.api.config.sharding.TableRuleConfiguration;
import org.apache.shardingsphere.api.config.sharding.strategy.StandardShardingStrategyConfiguration;
import org.apache.shardingsphere.core.rule.ShardingRule;
import org.apache.shardingsphere.core.rule.TableRule;
import org.apache.shardingsphere.shardingjdbc.jdbc.core.datasource.ShardingDataSource;
import org.apache.shardingsphere.underlying.common.rule.DataNode;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Jianbo.Peng <pengjianbosoft@gmail.com>
 * @date 2021/10/12 5:08 下午
 */
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
