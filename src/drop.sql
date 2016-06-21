/*
	Eric Eckert
    Homework 7
    CSE 414 Spring 16
    Suciu

	Drops Customer and Reservation tables if there is a bug or error in data or schema.
*/

drop table Reservation;
drop table Customer;
update FLIGHTS
set seats_reserved = 0;