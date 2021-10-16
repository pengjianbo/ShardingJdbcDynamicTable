package cc.bbmax.shardingjdbc.dynamictable.sharding;

import com.google.common.collect.Range;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shardingsphere.api.sharding.standard.PreciseShardingAlgorithm;
import org.apache.shardingsphere.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.api.sharding.standard.RangeShardingAlgorithm;
import org.apache.shardingsphere.api.sharding.standard.RangeShardingValue;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Jianbo.Peng <pengjianbosoft@gmail.com>
 * @date 2021/10/13 3:55 下午
 */
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
