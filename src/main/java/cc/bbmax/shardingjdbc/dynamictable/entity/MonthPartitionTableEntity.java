package cc.bbmax.shardingjdbc.dynamictable.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import java.util.Date;

/**
 * @author Jianbo.Peng <pengjianbosoft@gmail.com>
 * @date 2021/10/13 4:38 下午
 */
@Entity
@Setter
@Getter
@Table(name = "month_partition_table")
@ToString
public class MonthPartitionTableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String column1;

    @Column(nullable = false)
    private Date partitionDate;
}
