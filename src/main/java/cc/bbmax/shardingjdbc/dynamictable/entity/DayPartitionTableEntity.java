package cc.bbmax.shardingjdbc.dynamictable.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * @author Jianbo.Peng <pengjianbosoft@gmail.com>
 * @date 2021/10/13 4:38 下午
 */
@Entity
@Setter
@Getter
@Table(name = "day_partition_table")
@ToString
public class DayPartitionTableEntity implements Serializable {

    private static final long serialVersionUID = -1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String column1;

    @Column(nullable = false)
    private Date partitionDate;
}
