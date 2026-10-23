package com.example.auction;

import com.zaxxer.hikari.HikariDataSource;
import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Persistent local relational database; application connections use a non-admin user. */
@Configuration
@ConditionalOnProperty(name="auction.local-db",havingValue="true",matchIfMissing=true)
public class LocalDatabaseConfiguration {
    @Bean
    public DataSource dataSource(Environment environment) throws Exception {
        Path file=Path.of(environment.getProperty("auction.local-db-path","./data/auction")).toAbsolutePath().normalize();
        Files.createDirectories(file.getParent());
        Path envFile=Path.of(environment.getProperty("auction.credentials-path",".env"));
        Properties secrets=new Properties();
        if(Files.exists(envFile)) try(var input=Files.newInputStream(envFile)) { secrets.load(input); }
        boolean changed=false;
        for(String name:List.of("H2_ADMIN_PASSWORD","H2_APP_PASSWORD")) {
            if(secrets.getProperty(name,"").isBlank()) {
                byte[] bytes=new byte[24]; new SecureRandom().nextBytes(bytes);
                secrets.setProperty(name,HexFormat.of().formatHex(bytes)); changed=true;
            }
        }
        if(changed) try(var output=Files.newOutputStream(envFile)) { secrets.store(output,"Local database credentials. Do not publish this file."); }
        String url="jdbc:h2:file:"+file.toString().replace('\\','/');
        String appPassword=secrets.getProperty("H2_APP_PASSWORD");
        try(var admin=DriverManager.getConnection(url,"sa",secrets.getProperty("H2_ADMIN_PASSWORD"));var statement=admin.createStatement()) {
            statement.execute("CREATE USER IF NOT EXISTS auction_app PASSWORD '"+appPassword.replace("'","''")+"'");
            boolean initialized;
            try(var rs=statement.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='APP_USERS'")) {
                rs.next(); initialized=rs.getInt(1)>0;
            }
            if(!initialized) {
                ScriptUtils.executeSqlScript(admin,new ClassPathResource("db/h2-schema.sql"));
                ScriptUtils.executeSqlScript(admin,new ClassPathResource("db/h2-demo.sql"));
            }
            statement.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON app_users,collectible_items,auction_lots TO auction_app");
            statement.execute("GRANT SELECT,INSERT ON bids TO auction_app");
            statement.execute("GRANT SELECT,INSERT,UPDATE ON auction_deals TO auction_app");
            statement.execute("GRANT SELECT,UPDATE ON auction_mutex TO auction_app");
        }
        var pool=new HikariDataSource();
        pool.setJdbcUrl(url+";IFEXISTS=TRUE");
        pool.setUsername("auction_app"); pool.setPassword(appPassword);
        pool.setMaximumPoolSize(5);
        return pool;
    }
}
