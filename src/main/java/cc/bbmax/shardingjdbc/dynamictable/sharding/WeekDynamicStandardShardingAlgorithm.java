package cc.bbmax.shardingjdbc.dynamictable.sharding;

/**
 * @author Jianbo.Peng <pengjianbosoft@gmail.com>
 * @date 2021/10/13 3:58 下午
 */
public class WeekDynamicStandardShardingAlgorithm extends DynamicStandardShardingAlgorithm {

    public WeekDynamicStandardShardingAlgorithm() {
        super(WEEK_DATE_FORMAT);
    }
}
