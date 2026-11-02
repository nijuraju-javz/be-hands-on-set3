# Meeting Room Booking Service

A RESTful service for booking meeting rooms and finding common free slots among attendees, built with Java 21 and Spring Boot 3.3.

## Overview
This service provides meeting room reservation functionality, schedule validation, clash detection, attendee-based free slot search, and concurrency protection under high load.

## Questions & Pull Requests
| # | Question | PR link |
|---|---|---|
| 1 | Room Booking API | https://github.com/nijuraju-javz/be-hands-on-set3/pull/1 |
| 2 | Free Slots & Safe Booking | https://github.com/nijuraju-javz/be-hands-on-set3/pull/2 |

## Requirements
- Java 21+
- Maven wrapper (`./mvnw` or `mvnw.cmd`)

## Running the Application
```bash
./mvnw spring-boot:run
```
The server starts on port `8080`.

## Running the Tests
```bash
./mvnw test
```
