package com.example.auction;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/{resource}")
public class AuctionController {
 private final AuctionService service;
 public AuctionController(AuctionService service) { this.service=service; }
 @GetMapping public List<Map<String,Object>> all(@PathVariable String resource) { return service.all(resource); }
 @GetMapping("/{id}") public Map<String,Object> one(@PathVariable String resource,@PathVariable long id) { return service.get(resource,id); }
 @PostMapping public ResponseEntity<Map<String,Object>> create(@PathVariable String resource,@RequestBody Map<String,Object> body) {
  var result=service.create(resource,body);
  return ResponseEntity.created(URI.create("/api/"+resource+"/"+result.get("id"))).body(result);
 }
 @PatchMapping("/{id}") public Map<String,Object> patch(@PathVariable String resource,@PathVariable long id,@RequestBody Map<String,Object> body) { return service.update(resource,id,body,false); }
 @PutMapping("/{id}") public Map<String,Object> put(@PathVariable String resource,@PathVariable long id,@RequestBody Map<String,Object> body) { return service.update(resource,id,body,true); }
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void delete(@PathVariable String resource,@PathVariable long id) { service.delete(resource,id); }
}
