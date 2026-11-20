package com.example.auction;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;
@RestController @RequestMapping("/api/{resource}") public class AuctionController {
 private final AuctionService service;
 public AuctionController(AuctionService service){this.service=service;}
 @GetMapping public List<?> all(@PathVariable String resource){return service.all(resource);}
 @GetMapping("/{id}") public Object one(@PathVariable String resource,@PathVariable Long id){return service.get(resource,id);}
 @PostMapping public ResponseEntity<Object> create(@PathVariable String resource,@RequestBody Map<String,Object> data){Object result=service.save(resource,null,data);Long id=switch(result){case Model.User x->x.id();case Model.Item x->x.id();case Model.Lot x->x.id();case Model.Bid x->x.id();case Model.Deal x->x.id();default->throw new IllegalStateException();};return ResponseEntity.created(URI.create("/api/"+resource+"/"+id)).body(result);}
 @PatchMapping("/{id}") public Object patch(@PathVariable String resource,@PathVariable Long id,@RequestBody Map<String,Object> data){return service.save(resource,id,data);}
 @PutMapping("/{id}") public Object put(@PathVariable String resource, @PathVariable Long id, @RequestBody Map<String, Object> data) {return service.save(resource, id, data);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String resource,@PathVariable Long id){service.delete(resource,id);}
}
