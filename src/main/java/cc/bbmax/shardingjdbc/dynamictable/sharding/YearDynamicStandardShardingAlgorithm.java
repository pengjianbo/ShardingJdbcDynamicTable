package cc.bbmax.shardingjdbc.dynamictable.sharding;

/**
 * @author Jianbo.Peng <pengjianbosoft@gmail.com>
 * @date 2021/10/13 3:58 下午
 */
public class YearDynamicStandardShardingAlgorithm extends DynamicStandardShardingAlgorithm {

    public YearDynamicStandardShardingAlgorithm() {
        super(YEAR_DATE_FORMAT);
    }
}
