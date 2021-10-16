import cc.bbmax.shardingjdbc.dynamictable.ShardingJdbcDynamicTableApp;
import cc.bbmax.shardingjdbc.dynamictable.entity.DayPartitionTableEntity;
import cc.bbmax.shardingjdbc.dynamictable.entity.MonthPartitionTableEntity;
import cc.bbmax.shardingjdbc.dynamictable.entity.WeekPartitionTableEntity;
import cc.bbmax.shardingjdbc.dynamictable.entity.YearPartitionTableEntity;
import cc.bbmax.shardingjdbc.dynamictable.repository.DayPartitionTableRepository;
import cc.bbmax.shardingjdbc.dynamictable.repository.MonthPartitionTableRepository;
import cc.bbmax.shardingjdbc.dynamictable.repository.WeekPartitionTableRepository;
import cc.bbmax.shardingjdbc.dynamictable.repository.YearPartitionTableRepository;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;

/**
 * @author Jianbo.Peng <pengjianbosoft@gmail.com>
 * @date 2021/10/13 4:48 下午
 */
@SpringBootTest(classes = ShardingJdbcDynamicTableApp.class)
@Slf4j
public class SpringBootTests {

    @Autowired
    DayPartitionTableRepository dayPartitionTableRepository;

    @Autowired
    MonthPartitionTableRepository monthPartitionTableRepository;

    @Autowired
    WeekPartitionTableRepository weekPartitionTableRepository;

    @Autowired
    YearPartitionTableRepository yearPartitionTableRepository;

    @Test
    public void testAddData() {
        DateTime dt = new DateTime(2021, 1, 1, 0, 0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        for (int i = 0; i < 10; i++) {
            DayPartitionTableEntity entity = new DayPartitionTableEntity();
            entity.setColumn1("data " + sdf.format(dt.toDate()));
            entity.setPartitionDate(dt.toDate());
            dayPartitionTableRepository.save(entity);
            dt = dt.plusDays(1);
        }

        dt = new DateTime(2021, 1, 1, 0, 0);
        for (int i = 0; i < 10; i++) {
            WeekPartitionTableEntity entity = new WeekPartitionTableEntity();
            entity.setColumn1("data " + sdf.format(dt.toDate()));
            entity.setPartitionDate(dt.toDate());
            weekPartitionTableRepository.save(entity);
            dt = dt.plusWeeks(1);
        }

        dt = new DateTime(2021, 1, 1, 0, 0);
        for (int i = 0; i < 10; i++) {
            MonthPartitionTableEntity entity = new MonthPartitionTableEntity();
            entity.setColumn1("data " + sdf.format(dt.toDate()));
            entity.setPartitionDate(dt.toDate());
            monthPartitionTableRepository.save(entity);
            dt = dt.plusMonths(1);
        }

        dt = new DateTime(2019, 1, 1, 0, 0);
        for (int i = 0; i < 3; i++) {
            YearPartitionTableEntity entity = new YearPartitionTableEntity();
            entity.setColumn1("data " + sdf.format(dt.toDate()));
            entity.setPartitionDate(dt.toDate());
            yearPartitionTableRepository.save(entity);
            dt = dt.plusYears(1);
        }
    }


    @Test
    public void testQuery() {
        DateTime dt = new DateTime(2021, 1, 1, 0, 0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        for (int i = 0; i < 10; i++) {
            String data = "data " + sdf.format(dt.toDate());
            Optional<DayPartitionTableEntity> optional = dayPartitionTableRepository.findFirstByColumn1(data);
            if (optional.isPresent()) {
                DayPartitionTableEntity entity = optional.get();

                log.info("testQuery result=" + entity.toString());
            }
            dt = dt.plusDays(1);
        }

        dt = new DateTime(2021, 1, 1, 0, 0);
        for (int i = 0; i < 10; i++) {
            String data = "data " + sdf.format(dt.toDate());
            Optional<WeekPartitionTableEntity> optional = weekPartitionTableRepository.findFirstByColumn1(data);
            if (optional.isPresent()) {
                WeekPartitionTableEntity entity = optional.get();

                log.info("testQuery result=" + entity.toString());
            }
            dt = dt.plusWeeks(1);
        }

        dt = new DateTime(2021, 1, 1, 0, 0);
        for (int i = 0; i < 10; i++) {
            String data = "data " + sdf.format(dt.toDate());
            Optional<MonthPartitionTableEntity> optional = monthPartitionTableRepository.findFirstByColumn1(data);
            if (optional.isPresent()) {
                MonthPartitionTableEntity entity = optional.get();

                log.info("testQuery result=" + entity.toString());
            }
            dt = dt.plusMonths(1);
        }

        dt = new DateTime(2019, 1, 1, 0, 0);
        for (int i = 0; i < 3; i++) {
            String data = "data " + sdf.format(dt.toDate());
            Optional<YearPartitionTableEntity> optional = yearPartitionTableRepository.findFirstByColumn1(data);
            if (optional.isPresent()) {
                YearPartitionTableEntity entity = optional.get();

                log.info("testQuery result=" + entity.toString());
            }
            dt = dt.plusYears(1);
        }
    }

    @Test
    public void testQueryDateBetween() {
        List<DayPartitionTableEntity> dayResultList = dayPartitionTableRepository.findByPartitionDateBetween(
                new DateTime(2021, 1, 1, 0, 0).toDate(),
                new DateTime(2021, 1, 10, 0, 0).toDate());
        for (DayPartitionTableEntity entity : dayResultList) {
            log.info("testQueryDateBetween result=" + entity.toString());
        }

        List<WeekPartitionTableEntity> weekResultList = weekPartitionTableRepository.findByPartitionDateBetween(
                new DateTime(2021, 1, 1, 0, 0).toDate(),
                new DateTime(2021, 1, 1, 0, 0).plusWeeks(10).toDate());
        for (WeekPartitionTableEntity entity : weekResultList) {
            log.info("testQueryDateBetween result=" + entity.toString());
        }

        List<MonthPartitionTableEntity> monthResultList = monthPartitionTableRepository.findByPartitionDateBetween(
                new DateTime(2021, 1, 1, 0, 0).toDate(),
                new DateTime(2021, 10, 1, 0, 0).toDate());
        for (MonthPartitionTableEntity entity : monthResultList) {
            log.info("testQueryDateBetween result=" + entity.toString());
        }

        List<YearPartitionTableEntity> yearResultList = yearPartitionTableRepository.findByPartitionDateBetween(
                new DateTime(2019, 1, 1, 0, 0).toDate(),
                new DateTime(2021, 1, 1, 0, 0).toDate());
        for (YearPartitionTableEntity entity : yearResultList) {
            log.info("testQueryDateBetween result=" + entity.toString());
        }
    }

}
