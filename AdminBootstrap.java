package com.example.auction;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final Accounts accounts; private final JdbcTemplate db; private final Environment env;
    public AdminBootstrap(Accounts accounts,JdbcTemplate db,Environment env){this.accounts=accounts;this.db=db;this.env=env;}
    public void run(ApplicationArguments args){
        if(db.queryForObject("select count(*) from accounts where role='ADMIN'",Long.class)==0){
            String password=env.getProperty("auction.admin-password");
            if(password==null)throw new IllegalStateException("Set AUCTION_ADMIN_PASSWORD (12–64 characters with upper/lowercase, number and symbol)");
            accounts.create(new Accounts.Registration("admin","Administrator","admin@example.com",password),"ADMIN");
        }
    }
}
