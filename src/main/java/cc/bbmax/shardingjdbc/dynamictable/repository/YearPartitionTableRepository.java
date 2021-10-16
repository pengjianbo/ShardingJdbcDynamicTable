package cc.bbmax.shardingjdbc.dynamictable.repository;

import cc.bbmax.shardingjdbc.dynamictable.entity.YearPartitionTableEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * @author Jianbo.Peng <pengjianbosoft@gmail.com>
 * @date 2021/10/13 4:41 下午
 */
public interface YearPartitionTableRepository extends CrudRepository<YearPartitionTableEntity, Long> {
    Optional<YearPartitionTableEntity> findFirstByColumn1(String col);

    List<YearPartitionTableEntity> findByPartitionDateBetween(Date startDate, Date endDate);
}
