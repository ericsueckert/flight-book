/*
	Eric Eckert
	Homework 7
	CSE 414 Spring 16
	Suciu
	
	Create tables Customer and Reservation to hold user data
*/

create table Customer (
	cid int,
	username varchar(250),
	lastName varchar(250),
	firstName varchar(250),
	zipcode varchar(10),
	password varchar(250),
	primary key(cid)
);

insert into Customer values(1338722, 'ericsueckert', 'Eckert', 'Eric', '95129', 'potato');
insert into Customer values(1332958, 'd95wang', 'Wang', 'Derek', '98105', 'peanut');
insert into Customer values(1290478, 'wavinwee', 'Lee', 'Davin', '98149', 'obama');
insert into Customer values(1059294, 'KGod', 'Lim', 'Brian', '98379', 'dogK');
insert into Customer values(1582038, 'yugi', 'Gilbert', 'Alex', '95120', 'saberbutt');
insert into Customer values(1987646, 'nick.pong', 'Pong', 'Nick', '98478', 'salt_shaker');
insert into Customer values(1467894, 'jyang95', 'Yang', 'Jeanette', '95583', 'imsofat');
insert into Customer values(1548483, 'infinite_sass', 'Ma', 'Karen', '92092', 'imdumb');
insert into Customer values(1438759, 'chintrovert', 'Chin', 'Elisabeth', '98105', 'closet-weeb');


create table Reservation (
    rid int IDENTITY(1,1),
	cid int references Customer(cid),
	fid int references FLIGHTS(fid),
	primary key(rid),
	constraint uniqueRes unique(cid, fid)
);

insert into Reservation values(1338722, 720090);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 720090;
insert into Reservation values(1338722, 39432);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 39432;
insert into Reservation values(1338722, 43);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 43;
insert into Reservation values(1338722, 43850);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 43850;
insert into Reservation values(1332958, 1000000);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 1000000;
insert into Reservation values(1332958, 303013);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 303013;
insert into Reservation values(1290478, 303013);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 303013;
insert into Reservation values(1582038, 594854);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 594854;
insert into Reservation values(1582038, 34928);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 34928;
insert into Reservation values(1582038, 989438);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 989438;
insert into Reservation values(1987646, 459899);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 459899;
insert into Reservation values(1987646, 548378);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 548378;
insert into Reservation values(1467894, 2);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 2;
insert into Reservation values(1548483, 888888);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 888888;
insert into Reservation values(1548483, 666666);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 666666;
insert into Reservation values(1438759, 11);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 11;
insert into Reservation values(1438759, 111);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 111;
insert into Reservation values(1438759, 1111);
update FLIGHTS
set seats_reserved = seats_reserved + 1
where fid = 1111;