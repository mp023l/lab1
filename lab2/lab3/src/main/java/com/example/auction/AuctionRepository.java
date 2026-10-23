package com.example.auction;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Repository
public class AuctionRepository {
 private final JdbcTemplate jdbc;
 public AuctionRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
 // Serializes mutations across application instances; intentionally simple for this project.
 void lock() { jdbc.queryForObject("SELECT id FROM auction_mutex WHERE id=1 FOR UPDATE",Long.class); }
 private Map<String,Object> row(Resource r,ResultSet rs) throws SQLException {
  Map<String,Object> result=new LinkedHashMap<>(); result.put("id",rs.getLong("id"));
  for(var field:r.columns.entrySet()) {
   Object value=rs.getObject(field.getValue());
   if(value instanceof OffsetDateTime t) value=t.toInstant();
   if(value instanceof Timestamp t) value=t.toInstant();
   result.put(field.getKey(),value);
  }
  return result;
 }
 List<Map<String,Object>> all(Resource r) {
  return jdbc.query("SELECT * FROM "+r.table+" ORDER BY id",(rs,n)->row(r,rs));
 }
 Map<String,Object> get(Resource r,long id) {
  return jdbc.query("SELECT * FROM "+r.table+" WHERE id=?",(rs,n)->row(r,rs),id).stream().findFirst()
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,r.path+" not found: "+id));
 }
 private Object sqlValue(Object v) { return v instanceof Instant t?OffsetDateTime.ofInstant(t,ZoneOffset.UTC):v; }
 Map<String,Object> insert(Resource r,Map<String,Object> values) {
  var keys=new ArrayList<>(values.keySet());
  String columns=String.join(",",keys.stream().map(r.columns::get).toList());
  String marks=String.join(",",Collections.nCopies(keys.size(),"?"));
  var holder=new GeneratedKeyHolder();
  jdbc.update(connection->{
   var ps=connection.prepareStatement("INSERT INTO "+r.table+" ("+columns+") VALUES ("+marks+")",new String[]{"id"});
   for(int i=0;i<keys.size();i++) ps.setObject(i+1,sqlValue(values.get(keys.get(i))));
   return ps;
  },holder);
  return get(r,Objects.requireNonNull(holder.getKey()).longValue());
 }
 Map<String,Object> update(Resource r,long id,Map<String,Object> values) {
  String assignments=String.join(",",values.keySet().stream().map(k->r.columns.get(k)+"=?").toList());
  var args=new ArrayList<>(values.values().stream().map(this::sqlValue).toList()); args.add(id);
  jdbc.update("UPDATE "+r.table+" SET "+assignments+" WHERE id=?",args.toArray());
  return get(r,id);
 }
 void delete(Resource r,long id) { jdbc.update("DELETE FROM "+r.table+" WHERE id=?",id); }
}
