package com.example.auction;

import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.util.Map;

@Component("access")
public class Access {
    private final Accounts accounts;
    private final AuctionRepository r;
    public Access(Accounts accounts,AuctionRepository r){this.accounts=accounts;this.r=r;}
    public Accounts.Account current(){return accounts.account(SecurityContextHolder.getContext().getAuthentication().getName());}
    public boolean admin(){return current().role().equals("ADMIN");}
    private boolean mine(Long id){return id!=null && id==current().id();}
    public boolean read(String type,Long id) {
        if(admin())return true;
        return switch(type) {
            case "users" -> mine(id);
            case "auction-deals" -> {var d=r.deals.get(id);yield d!=null && participant(d);}
            default -> true;
        };
    }
    boolean participant(Model.Deal d){var lot=r.lots.get(d.lotId()); var bid=r.bids.get(d.winningBidId());var item=lot==null?null:r.items.get(lot.itemId());return item!=null && (mine(r.lotOwners.get(d.lotId())) || bid!=null && mine(bid.userId()));}
    public boolean write(String type,Long id,Map<String,Object> data) {
        if(type.equals("users"))return id!=null && (admin()||mine(id)) && !data.containsKey("role") && !data.containsKey("state") && !data.containsKey("password");
        if(type.equals("bids"))return id==null && (!data.containsKey("userId")||String.valueOf(current().id()).equals(String.valueOf(data.get("userId"))));
        if(type.equals("collectible-items")) {
            var item=id==null?null:r.items.get(id);
            return (id==null||item!=null&&(admin()||mine(item.ownerId()))) && (admin()||!data.containsKey("ownerId")||String.valueOf(current().id()).equals(String.valueOf(data.get("ownerId"))));
        }
        if(type.equals("auction-lots")) {
            var lot=id==null?null:r.lots.get(id);
            if(id!=null && (lot==null || !(admin()||mine(r.lotOwners.get(id)))))return false;
            if(data.containsKey("itemId")){try{return owns(Long.valueOf(data.get("itemId").toString()));}catch(RuntimeException e){return false;}}
            return id!=null;
        }
        if(type.equals("auction-deals")) {
            try {var d=id==null?null:r.deals.get(id);Long lotId=d==null?Long.valueOf(data.get("lotId").toString()):d.lotId();var lot=r.lots.get(lotId);return lot!=null&&(admin()||mine(r.lotOwners.get(lotId)));}catch(RuntimeException e){return false;}
        }
        return admin();
    }
    private boolean owns(Long itemId){var item=r.items.get(itemId);return item!=null&&(admin()||mine(item.ownerId()));}
    public boolean delete(String type,Long id){return admin();}
    public void denied(){throw new AccessDeniedException("Forbidden");}
}
