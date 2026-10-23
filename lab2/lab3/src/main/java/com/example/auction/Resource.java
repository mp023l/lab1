package com.example.auction;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
/** Whitelist: request text is never interpolated into SQL identifiers. */
public enum Resource {
 USERS("users","app_users","name","name","email","email"),
 ITEMS("collectible-items","collectible_items","description","description","ownerId","owner_id","condition","item_condition"),
 LOTS("auction-lots","auction_lots","itemId","item_id","startPrice","start_price","step","bid_step","startTime","start_time","endTime","end_time","status","status"),
 BIDS("bids","bids","lotId","lot_id","userId","user_id","amount","amount","time","bid_time"),
 DEALS("auction-deals","auction_deals","lotId","lot_id","winningBidId","winning_bid_id","amount","amount","transferStatus","transfer_status");
 final String path,table;
 final LinkedHashMap<String,String> columns=new LinkedHashMap<>();
 Resource(String path,String table,String... fields) {
  this.path=path; this.table=table;
  for(int i=0;i<fields.length;i+=2) columns.put(fields[i],fields[i+1]);
 }
 static Resource of(String path) {
  return Arrays.stream(values()).filter(r->r.path.equals(path)).findFirst()
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown resource"));
 }
}
