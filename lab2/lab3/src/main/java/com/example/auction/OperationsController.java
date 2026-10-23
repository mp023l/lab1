package com.example.auction;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api")
public class OperationsController {
 private final AuctionService service;
 public OperationsController(AuctionService service) { this.service=service; }
 @PostMapping("/auction-lots/{id}/publish") public Map<String,Object> publish(@PathVariable long id) { return service.publish(id); }
 @PostMapping("/auction-lots/{id}/place-bid") @ResponseStatus(HttpStatus.CREATED)
 public Map<String,Object> bid(@PathVariable long id,@RequestBody Map<String,Object> body) { return service.bid(id,body); }
 @PostMapping("/auction-lots/{id}/close") public Map<String,Object> close(@PathVariable long id) { return service.close(id); }
 @PostMapping("/auction-lots/{id}/cancel") public Map<String,Object> cancel(@PathVariable long id) { return service.cancel(id); }
 @PostMapping("/auction-deals/{id}/complete") public Map<String,Object> complete(@PathVariable long id) { return service.complete(id); }
}
