package com.example.auction;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import static org.junit.jupiter.api.Assertions.*;

class LocalDatabaseFileTest {
 @TempDir Path directory;
 @Test void fileDatabasePersistsAndApplicationHasRestrictedPermissions() throws Exception {
  var environment=new MockEnvironment()
   .withProperty("auction.local-db-path",directory.resolve("auction").toString())
   .withProperty("auction.credentials-path",directory.resolve(".env").toString());
  var config=new LocalDatabaseConfiguration();
  try(var pool=(HikariDataSource)config.dataSource(environment)) {
   var jdbc=new JdbcTemplate(pool);
   assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM app_users",Integer.class));
   jdbc.update("INSERT INTO app_users(name,email) VALUES ('Saved user','saved@example.org')");
   assertThrows(DataAccessException.class,()->jdbc.execute("CREATE TABLE forbidden(id int)"));
   assertThrows(DataAccessException.class,()->jdbc.update("DELETE FROM bids WHERE 1=0"));
   assertThrows(DataAccessException.class,()->jdbc.update("UPDATE bids SET amount=1 WHERE 1=0"));
   assertThrows(DataAccessException.class,()->jdbc.update("DELETE FROM auction_deals WHERE 1=0"));
   assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO auction_lots(item_id,start_price,bid_step,start_time,end_time,status) SELECT item_id,start_price,bid_step,start_time,end_time,status FROM auction_lots WHERE id=1"));
  }
  assertTrue(Files.exists(directory.resolve("auction.mv.db")));
  assertTrue(Files.exists(directory.resolve(".env")));
  try(var pool=(HikariDataSource)config.dataSource(environment)) {
   var jdbc=new JdbcTemplate(pool);
   assertEquals(4,jdbc.queryForObject("SELECT count(*) FROM app_users",Integer.class));
   assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM app_users WHERE email='saved@example.org'",Integer.class));
  }
 }
}