# Вариант 18 — Аукцион коллекционных предметов

Java 21, Spring Boot 3.4.4, Maven. Данные хранятся в памяти и исчезают при перезапуске.

## Запуск
Откройте папку проекта в IntelliJ IDEA как Maven-проект, дождитесь загрузки зависимостей. Запустите `AuctionApplication.java` или выполните `mvn spring-boot:run`.

## Эндпоинты
Для каждого из `users`, `collectible-items`, `auction-lots`, `bids`, `auction-deals` доступны:
- `GET /api/{resource}`
- `GET /api/{resource}/{id}`
- `POST /api/{resource}`
- `PATCH /api/{resource}/{id}`
- `DELETE /api/{resource}/{id}`

## Примеры JSON для POST
`/api/users`: `{"name":"Anna","email":"anna@example.com"}`

`/api/collectible-items`: `{"description":"Rare coin","ownerId":1,"condition":"GOOD"}`

`/api/auction-lots`: `{"itemId":2,"startPrice":100,"step":10,"startTime":"2026-01-01T00:00:00Z","endTime":"2027-01-01T00:00:00Z","status":"OPEN"}`

`/api/bids`: `{"lotId":3,"userId":4,"amount":110}` (создайте второго пользователя до ставки; ID зависят от последовательности создания)

`/api/auction-deals`: `{"lotId":3,"winningBidId":5,"amount":110,"transferStatus":"PENDING"}` (лот должен завершиться или иметь статус CLOSED)

Для PATCH передавайте только изменяемые поля, например `{"condition":"EXCELLENT"}`. Ставки нельзя редактировать, поскольку это история аукциона. Время ставки устанавливает сервер.

HTTP: чтение/изменение 200, создание 201 с Location, удаление 204, отсутствие 404, неверные данные 400.
