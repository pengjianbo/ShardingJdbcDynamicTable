package cc.bbmax.shardingjdbc.dynamictable.sharding;

/**
 * @author Jianbo.Peng <pengjianbosoft@gmail.com>
 * @date 2021/10/13 3:58 下午
 */
public class DayDynamicStandardShardingAlgorithm extends DynamicStandardShardingAlgorithm {

    public DayDynamicStandardShardingAlgorithm() {
        super(DAY_DATE_FORMAT);
    }
}
