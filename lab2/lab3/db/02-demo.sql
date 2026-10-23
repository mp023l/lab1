-- Dates relative to initialization: no hardcoded year dependency.
INSERT INTO app_users(name,email) VALUES ('Анна — продавец','anna@example.com'),('Иван — участник','ivan@example.com'),('Мария — участник','maria@example.com');
INSERT INTO collectible_items(description,owner_id,item_condition) VALUES
('Серебряная коллекционная монета',1,'EXCELLENT'),('Винтажная почтовая марка',1,'GOOD'),('Фарфоровая статуэтка',3,'MINT');
INSERT INTO auction_lots(item_id,start_price,bid_step,start_time,end_time,status) VALUES
(1,1000,100,CURRENT_TIMESTAMP-INTERVAL '1 hour',CURRENT_TIMESTAMP+INTERVAL '1 day','OPEN'),
(2,500,50,CURRENT_TIMESTAMP-INTERVAL '2 days',CURRENT_TIMESTAMP-INTERVAL '1 day','CLOSED'),
(3,2000,100,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '2 days','DRAFT');
INSERT INTO bids(lot_id,user_id,amount,bid_time) VALUES
(1,2,1000,CURRENT_TIMESTAMP-INTERVAL '10 minutes'),
(2,2,500,CURRENT_TIMESTAMP-INTERVAL '36 hours'),
(2,3,550,CURRENT_TIMESTAMP-INTERVAL '35 hours');
INSERT INTO auction_deals(lot_id,winning_bid_id,amount,transfer_status) VALUES (2,3,550,'PENDING');
