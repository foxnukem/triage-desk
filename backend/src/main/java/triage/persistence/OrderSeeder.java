package triage.persistence;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/** Loads db/seed/orders.sql (classpath:seed/orders.sql) when the orders table is empty. */
@Component
public class OrderSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderSeeder.class);

    private final OrderRepository orders;
    private final DataSource dataSource;

    public OrderSeeder(OrderRepository orders, DataSource dataSource) {
        this.orders = orders;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (orders.count() == 0) {
            var populator = new ResourceDatabasePopulator(new ClassPathResource("seed/orders.sql"));
            populator.setSqlScriptEncoding("UTF-8");
            populator.execute(dataSource);
            log.info("Seeded {} orders", orders.count());
        }
    }
}
