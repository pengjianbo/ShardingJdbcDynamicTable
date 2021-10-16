package cc.bbmax.shardingjdbc.dynamictable;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author pengjianbo
 */
@SpringBootApplication(scanBasePackages = "cc.bbmax.shardingjdbc.dynamictable.*")
@EnableJpaAuditing
@EnableScheduling
@EntityScan({"cc.bbmax.shardingjdbc.dynamictable.entity"})
@EnableJpaRepositories({"cc.bbmax.shardingjdbc.dynamictable.repository"})
@EnableAsync
public class ShardingJdbcDynamicTableApp {

    public static void main(String[] args) {
        SpringApplication.run(ShardingJdbcDynamicTableApp.class, args);
    }
}
