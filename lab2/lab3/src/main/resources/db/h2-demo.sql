-- Dates relative to initialization: no hardcoded year dependency.
INSERT INTO app_users(name,email) VALUES ('Максимка — продавец','ver33@example.com'),('Льюис — участник','ham44@example.com'),('Шарль — участник','lec16@example.com');
INSERT INTO collectible_items(description,owner_id,item_condition) VALUES
('Шлем',1,'EXCELLENT'),('Кубок конструктора',1,'GOOD'),('Чемпионство',3,'MINT');
INSERT INTO auction_lots(item_id,start_price,bid_step,start_time,end_time,status) VALUES
(1,1000,100,DATEADD('HOUR',-1,CURRENT_TIMESTAMP),DATEADD('DAY',1,CURRENT_TIMESTAMP),'OPEN'),
(2,500,50,DATEADD('DAY',-2,CURRENT_TIMESTAMP),DATEADD('DAY',-1,CURRENT_TIMESTAMP),'CLOSED'),
(3,2000,100,CURRENT_TIMESTAMP,DATEADD('DAY',2,CURRENT_TIMESTAMP),'DRAFT');
INSERT INTO bids(lot_id,user_id,amount,bid_time) VALUES
(1,2,1000,DATEADD('MINUTE',-10,CURRENT_TIMESTAMP)),
(2,2,500,DATEADD('HOUR',-36,CURRENT_TIMESTAMP)),
(2,3,550,DATEADD('HOUR',-35,CURRENT_TIMESTAMP));
INSERT INTO auction_deals(lot_id,winning_bid_id,amount,transfer_status) VALUES (2,3,550,'PENDING');
