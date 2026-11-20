package com.example.auction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class UserSessionRepository {
    public record Session(String id,long accountId,String refreshJti,String refreshHash,Instant expiresAt,SessionStatus status) {}
    private final JdbcTemplate db;
    public UserSessionRepository(JdbcTemplate db){this.db=db;}
    public void insert(String id,long accountId,String deviceId,String accessJti,String refreshJti,String hash,Instant expires){
        db.update("insert into user_sessions(id,account_id,device_id,access_jti,refresh_jti,refresh_hash,issued_at,expires_at,status) values(?,?,?,?,?,?,?,?,?)",
          id,accountId,deviceId,accessJti,refreshJti,hash,Timestamp.from(Instant.now()),Timestamp.from(expires),SessionStatus.ACTIVE.name());
    }
    // Row-level lock prevents two simultaneous refresh requests from both succeeding.
    public Optional<Session> locked(String id){
        return db.query("select * from user_sessions where id=? for update",(rs,n)->new Session(
            rs.getString("id"),rs.getLong("account_id"),rs.getString("refresh_jti"),rs.getString("refresh_hash"),
            rs.getTimestamp("expires_at").toInstant(),SessionStatus.valueOf(rs.getString("status"))),id).stream().findFirst();
    }
    public void status(String id,SessionStatus status){db.update("update user_sessions set status=? where id=?",status.name(),id);}
}
