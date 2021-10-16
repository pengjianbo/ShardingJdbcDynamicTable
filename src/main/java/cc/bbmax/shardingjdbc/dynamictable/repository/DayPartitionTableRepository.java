package cc.bbmax.shardingjdbc.dynamictable.repository;

import cc.bbmax.shardingjdbc.dynamictable.entity.DayPartitionTableEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * @author Jianbo.Peng <pengjianbosoft@gmail.com>
 * @date 2021/10/13 4:41 下午
 */
public interface DayPartitionTableRepository extends JpaRepository<DayPartitionTableEntity, Long> {


    Optional<DayPartitionTableEntity> findFirstByColumn1(String col);

    List<DayPartitionTableEntity> findByPartitionDateBetween(Date startDate, Date endDate);
}
