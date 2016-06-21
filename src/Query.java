import javax.xml.transform.Result;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.FileInputStream;

/**
 * Runs queries against a back-end database
 *
 * Eric Eckert
 * Homework 7
 * CSE 414 Spring 16
 * Suciu
 */
public class Query {

	private String configFilename;
	private Properties configProps = new Properties();

	private String jSQLDriver;
	private String jSQLUrl;
	private String jSQLUser;
	private String jSQLPassword;

	// DB Connection
	private Connection conn;

	// Logged In User
	private String username;
	private int cid = -1; // Unique customer ID

	//Last search result
	private ArrayList<int[]> itinerary = new ArrayList<int[]>();

	// Canned queries

	//search (one hop)
	private static final String SEARCH_ONE_HOP_SQL =
			"SELECT TOP (?) f.fid, w.day_of_week, m.month, day_of_month, year, c.name as carrier, f.flight_num, f.origin_city, "
					+ "f.dest_city, f.actual_time "
					+ "FROM FLIGHTS f, MONTHS m, CARRIERS c, WEEKDAYS w "
					+ "WHERE f.origin_city = ? AND f.dest_city = ? AND f.day_of_month = ? and f.year = 2015 and f.actual_time > 0 "
					+ "and f.month_id = m.mid and f.day_of_week_id = w.did and f.carrier_id = c.cid "
					+ "ORDER BY f.actual_time ASC";
	private PreparedStatement searchOneHopStatement;

	//Search two hop: searches for flights to desetination with 1 transfer, 2 legs
	private static final String SEARCH_TWO_HOP_SQL =
			"SELECT top (?) f1.fid, w1.day_of_week, m1.month, f1.day_of_month, f1.year, c1.name as carrier, f1.flight_num, "
					+ "f1.origin_city, f1.dest_city, f1.actual_time, f2.fid as fid2, w2.day_of_week as day_of_week2, "
					+ "m2.month as month2, f2.day_of_month as day_of_month2, f2.year as year2, c2.name as carrier2, "
					+ "f2.flight_num as flight_num2, f2.origin_city as origin_city2, f2.dest_city as dest_city2, "
					+ "f2.actual_time as actual_time2 "
					+ "FROM FLIGHTS f1, FLIGHTS f2, MONTHS m1, MONTHS m2, CARRIERS c1, CARRIERS c2, WEEKDAYS w1, WEEKDAYS w2 "
					+ "WHERE f1.origin_city = ? AND f2.dest_city = ? AND f1.day_of_month = ? and f2.day_of_month > f1.day_of_month "
					+ "and f1.year = 2015 and f2.year = 2015 and f1.dest_city = f2.origin_city and f2.actual_time > 0 "
					+ "and f1.actual_time > 0 and f1.month_id = m1.mid and f2.month_id = m2.mid and f1.day_of_week_id = w1.did "
					+ "and f2.day_of_week_id = w2.did "
					+ "and f1.carrier_id = c1.cid and f2.carrier_id = c2.cid "
					+ "ORDER BY f1.actual_time + f2.actual_time ASC";
	private PreparedStatement searchTwoHopStatement;

	//Search statement for customer login
	private static final String SEARCH_LOGIN_SQL = "select cid, password from Customer where username = ?";
	private PreparedStatement loginStatement;

	//Search statement for cancelation
	private static final String SEARCH_CANCEL_SQL = "select f.fid, f.seats_reserved "
			+ "from Reservation r, FLIGHTS f "
			+ "where r.rid = ? and r.cid = ? and r.fid = f.fid";
	private PreparedStatement cancelStatement;

	//Update statement to remove seat from flight
	private static final String REMOVE_SEAT_CANCEL_SQL = "update FLIGHTS set seats_reserved = seats_reserved - 1 where fid = ?";
	private PreparedStatement removeSeatStatement;

	//Delete statement to remove reservation from customer reservations
	private static final String DELETE_RESERVATION_CANCEL_SQL = "delete from Reservation where rid = ? and cid = ?";
	private PreparedStatement deleteReservationStatement;

	//Search statement to show reservations for customer
	private static final String RESERVATION_LIST_SQL = "select f.fid, w.day_of_week, m.month, f.day_of_month, f.year, "
			+ "c.name as carrier, f.flight_num, r.rid, f.origin_city, f.dest_city, f.actual_time, f.month_id "
			+ "from Reservation r, FLIGHTS f, MONTHS m, CARRIERS c, WEEKDAYS w "
			+ "where r.cid = ? and r.fid = f.fid and f.day_of_week_id = w.did and f.month_id = m.mid and f.carrier_id = c.cid "
			+ "order by r.rid";
	private PreparedStatement reservationListStatement;

	//Search statement to find flight to book
	private static final String SEARCH_BOOK_SQL = "select day_of_month, seats_reserved, max_reservations, month_id, year "
			+ "from FLIGHTS where fid = ?";
	private PreparedStatement bookStatement;

	//Update statement to add seat to flight
	private static final String ADD_SEAT_BOOK_SQL = "update FLIGHTS set seats_reserved = seats_reserved + 1 where fid = ?";
	private PreparedStatement addSeatStatement;

	//Instert statement to add reservation to customer reservations
	private static final String ADD_RESERVATION_BOOK_SQL = "insert into Reservation values(?, ?)";
	private PreparedStatement addReservationStatement;

	// transactions
	private static final String BEGIN_TRANSACTION_SQL =  
			"SET TRANSACTION ISOLATION LEVEL SERIALIZABLE; BEGIN TRANSACTION;"; 
	private PreparedStatement beginTransactionStatement;

	private static final String COMMIT_SQL = "COMMIT TRANSACTION";
	private PreparedStatement commitTransactionStatement;

	private static final String ROLLBACK_SQL = "ROLLBACK TRANSACTION";
	private PreparedStatement rollbackTransactionStatement;


	public Query(String configFilename) {
		this.configFilename = configFilename;
	}

	/**********************************************************/
	/* Connection code to SQL Azure.  */
	public void openConnection() throws Exception {
		configProps.load(new FileInputStream(configFilename));

		jSQLDriver   = configProps.getProperty("flightservice.jdbc_driver");
		jSQLUrl	   = configProps.getProperty("flightservice.url");
		jSQLUser	   = configProps.getProperty("flightservice.sqlazure_username");
		jSQLPassword = configProps.getProperty("flightservice.sqlazure_password");

		/* load jdbc drivers */
		Class.forName(jSQLDriver).newInstance();

		/* open connections to the flights database */
		conn = DriverManager.getConnection(jSQLUrl, // database
			jSQLUser, // user
			jSQLPassword); // password

		conn.setAutoCommit(true); //by default automatically commit after each statement 

		/* You will also want to appropriately set the 
                   transaction's isolation level through:  
		   conn.setTransactionIsolation(...) */

	}

	public void closeConnection() throws Exception {
		conn.close();
	}

	/**********************************************************/
	/* prepare all the SQL statements in this method.
      "preparing" a statement is almost like compiling it.  Note
       that the parameters (with ?) are still not filled in */

	public void prepareStatements() throws Exception {
		searchOneHopStatement = conn.prepareStatement(SEARCH_ONE_HOP_SQL);
 		beginTransactionStatement = conn.prepareStatement(BEGIN_TRANSACTION_SQL);
		commitTransactionStatement = conn.prepareStatement(COMMIT_SQL);
		rollbackTransactionStatement = conn.prepareStatement(ROLLBACK_SQL);
		loginStatement = conn.prepareStatement(SEARCH_LOGIN_SQL);
		cancelStatement = conn.prepareStatement(SEARCH_CANCEL_SQL);
		removeSeatStatement = conn.prepareStatement(REMOVE_SEAT_CANCEL_SQL);
		deleteReservationStatement = conn.prepareStatement(DELETE_RESERVATION_CANCEL_SQL);
		reservationListStatement = conn.prepareStatement(RESERVATION_LIST_SQL);
		bookStatement = conn.prepareStatement(SEARCH_BOOK_SQL);
		addSeatStatement = conn.prepareStatement(ADD_SEAT_BOOK_SQL);
		addReservationStatement = conn.prepareStatement(ADD_RESERVATION_BOOK_SQL);
		searchTwoHopStatement = conn.prepareStatement(SEARCH_TWO_HOP_SQL);
	}

	/*
		Logs the user in and stores their customer ID. Checks username and password.
		Throws IllegalArgumentException for invalid username or password
		Exits if username or password is incorrect
	*/
	public void transaction_login(String username, String password) throws Exception {
		//Throw exceptions for null values
		if (username == null || password == null) {
			throw new IllegalArgumentException("Username or password is null.");
		}
		//Check for spaces in username and password for invalidity
		if (username.contains(" ") || password.contains(" ")) {
			System.out.println("Password or username incorrect or invalid");
			return;
		}
		//Store username in global variable
		this.username = username;

		beginTransaction();

		//Set parameters for login statement
		loginStatement.clearParameters();
		loginStatement.setString(1,username);
		//Execute query
		ResultSet loginResult = loginStatement.executeQuery();

		//Check if there are results, if so then checks if password matches username/password pair.
		if (loginResult.next()) {
			String result_pass = loginResult.getString("password");
			if (password.equals(result_pass)) {
				//If password is correct, register customer id
				cid = loginResult.getInt("cid");
				//If customer ID is invalid, throw exception
				if (cid <= 0) {
					throw new IllegalArgumentException("User does not have a valid customer ID.");
				}
				System.out.println("You are now logged in as \"" + username + "\"");
				commitTransaction();
				loginResult.close();
				return;
			}
		}
		// If username is wrong or password is wrong print error
		System.out.println("Password or username incorrect or invalid");
		loginResult.close();
		rollbackTransaction();
	}

	/**
	 * Searches for flights from the given origin city to the given destination
	 * city, on the given day of the month. If "directFlight" is true, it only
	 * searches for direct flights, otherwise is searches for direct flights
	 * and flights with two "hops". Only searches for up to the number of
	 * itineraries given.
	 * Prints the results found by the search.
	 */
	public void transaction_search_safe(String originCity, String destinationCity, boolean directFlight, int dayOfMonth, int numberOfItineraries) throws Exception {
		//If origin city or destination city is null, throw exception
		if (originCity == null || destinationCity == null) {
			throw new IllegalArgumentException("The origin city or destination city was null");
		}
		//End search if day of month or number of Itineraries is invalid
		if (dayOfMonth <= 0 || dayOfMonth > 31 || numberOfItineraries < 0) {
			System.out.println("Invalid search.");
			return;
		}

		//Clear the itinerary search list for a new search
		itinerary.clear();

		// one hop itineraries
		if (directFlight) {
			//Enter parameters in direct flight search statement
			searchOneHopStatement.clearParameters();
			searchOneHopStatement.setInt(1, numberOfItineraries);
			searchOneHopStatement.setString(2, originCity);
			searchOneHopStatement.setString(3, destinationCity);
			searchOneHopStatement.setInt(4, dayOfMonth);
			ResultSet oneHopResults = searchOneHopStatement.executeQuery();

			//List the results under itinerary numbers
			while (oneHopResults.next()) {
				//Add each result to the itinerary
				int[] result_fid = {oneHopResults.getInt("fid")};
				itinerary.add(result_fid);

				//Grab and print out results
				String result_day_of_week = oneHopResults.getString("day_of_week");
				int result_year = oneHopResults.getInt("year");
				String result_month = oneHopResults.getString("month");
				int result_dayOfMonth = oneHopResults.getInt("day_of_month");
				String result_carrier = oneHopResults.getString("carrier");
				String result_flightNum = oneHopResults.getString("flight_num");
				String result_originCity = oneHopResults.getString("origin_city");
				String result_destinationCity = oneHopResults.getString("dest_city");
				int result_time = oneHopResults.getInt("actual_time");

				System.out.println("Itinerary " + oneHopResults.getRow() + "\t-- Flight date: " + result_day_of_week
						+ " " + result_month + " " + result_dayOfMonth + ", \t" + result_year + "\tCarrier: " + result_carrier
						+ "\tFlight number: " + result_flightNum + "\tDeparture: " + result_originCity + "\tDestination: "
						+ result_destinationCity + "\tFlight time: " + result_time);
			}
			oneHopResults.close();
		}

		//flights with transfers
		else {
			//enter parameters in transfer flight search statement
			searchTwoHopStatement.clearParameters();
			searchTwoHopStatement.setInt(1, numberOfItineraries);
			searchTwoHopStatement.setString(2, originCity);
			searchTwoHopStatement.setString(3, destinationCity);
			searchTwoHopStatement.setInt(4, dayOfMonth);
			ResultSet twoHopResults = searchTwoHopStatement.executeQuery();

			//List results inder itinerary numbers
			while (twoHopResults.next()) {
				//Add each result set to a single itinerary number
				int[] result_fids = {twoHopResults.getInt("fid"), twoHopResults.getInt("fid2")};
				itinerary.add(result_fids);

				//Grab and print results
				String result_day_of_week1 = twoHopResults.getString("day_of_week");
				int result_year1 = twoHopResults.getInt("year");
				String result_month1 = twoHopResults.getString("month");
				int result_dayOfMonth1 = twoHopResults.getInt("day_of_month");
				String result_carrier1 = twoHopResults.getString("carrier");
				String result_flightNum1 = twoHopResults.getString("flight_num");
				String result_originCity1 = twoHopResults.getString("origin_city");
				String result_destinationCity1 = twoHopResults.getString("dest_city");
				int result_time1 = twoHopResults.getInt("actual_time");

				String result_day_of_week2 = twoHopResults.getString("day_of_week2");
				int result_year2 = twoHopResults.getInt("year2");
				String result_month2 = twoHopResults.getString("month2");
				int result_dayOfMonth2 = twoHopResults.getInt("day_of_month2");
				String result_carrier2 = twoHopResults.getString("carrier2");
				String result_flightNum2 = twoHopResults.getString("flight_num2");
				String result_originCity2 = twoHopResults.getString("origin_city2");
				String result_destinationCity2 = twoHopResults.getString("dest_city2");
				int result_time2 = twoHopResults.getInt("actual_time2");

				System.out.println("Itinerary " + twoHopResults.getRow() + "\t-- (Flight 1) Flight date: " + result_day_of_week1
						+ " " + result_month1 + " " + result_dayOfMonth1 + ", \t" + result_year1 + "\tCarrier: " + result_carrier1
						+ "\tFlight number: " + result_flightNum1 + "\tDeparture: " + result_originCity1 + "\tDestination: "
						+ result_destinationCity1 + "\tFlight time: " + result_time1 + "\t-- (Flight 2) Flight date: "
						+ result_day_of_week2 + " " + result_month2 + " " + result_dayOfMonth2 + ", " + result_year2 + "\tCarrier: "
						+ result_carrier2 + "\tFlight number: " + result_flightNum2 + "\tDeparture: " + result_originCity2
						+ "\tDestination: " + result_destinationCity2 + "\tFlight time: " + result_time2);
			}
			twoHopResults.close();
		}
	}
	
//	public void transaction_search_unsafe(String originCity, String destinationCity, boolean directFlight, int dayOfMonth, int numberOfItineraries) throws Exception {
//
//            // one hop itineraries
//            String unsafeSearchSQL =
//                "SELECT TOP (" + numberOfItineraries +  ") year,month_id,day_of_month,carrier_id,flight_num,origin_city,actual_time "
//                + "FROM Flights "
//                + "WHERE origin_city = \'" + originCity + "\' AND dest_city = \'" + destinationCity +  "\' AND day_of_month =  "
//						+ dayOfMonth + " "
//                + "ORDER BY actual_time ASC";
//
//            System.out.println("Submitting query: " + unsafeSearchSQL);
//            Statement searchStatement = conn.createStatement();
//            ResultSet oneHopResults = searchStatement.executeQuery(unsafeSearchSQL);
//
//            while (oneHopResults.next()) {
//                int result_year = oneHopResults.getInt("year");
//                int result_monthId = oneHopResults.getInt("month_id");
//                int result_dayOfMonth = oneHopResults.getInt("day_of_month");
//                String result_carrierId = oneHopResults.getString("carrier_id");
//                String result_flightNum = oneHopResults.getString("flight_num");
//                String result_originCity = oneHopResults.getString("origin_city");
//                int result_time = oneHopResults.getInt("actual_time");
//                System.out.println("Flight: " + result_year + "," + result_monthId + "," + result_dayOfMonth + ","
//						+ result_carrierId + "," + result_flightNum + "," + result_originCity + "," + result_time);
//            }
//            oneHopResults.close();
//        }

	/*
		User, having made a previous search and being logged in, may enter a number indicating
		an itinerary from the search, and if there are no conflicts, may book it. The method will increased
		the number of seats reserved in the flight, and also add the flights in the itinerary to the user's
		reservation list.
		Throws IllegalArgumentException (through a private method) if number of seats in the flight is invalid.
		Exits if itinerary ID is invalid, or if user isn't logged in, or if user did not make a search,
		or if any of the flights in the itinerary are at full capacity, or if user has flight scheduled on the
		same day as any of the flights he is attempting to book
	*/
	public void transaction_book(int itineraryId) throws Exception {
		//If itinerary isn't valid, throw exception
		if (!(itineraryId > 0)) {
			throw new IllegalArgumentException("itineraryId is invalid");
		}
		//Check if user is not logged in. if so then end method.
		if (cid == -1) {
			System.out.println("You need to be logged in to perform this action.");
			return;
		}
		//Check if user searched made a search. if not, ends method
		if (itinerary.isEmpty()) {
			System.out.println("There are no flights in the search to book from.");
			return;
		}
		//Check if user entered a number not included in the itinerary.
		if (itineraryId > itinerary.size()) {
			System.out.println("Not a valid itinerary number.");
			return;
		}

		//Grab flight IDs of intended book from the itinerary list of the previous search
		int[] bookFids = itinerary.get(itineraryId - 1);

		/*
			Begin the transaction
		*/
		beginTransaction();
		//Check and book(if everything valid) each flight in the itinerary list
		for(int bookFid:bookFids) {
			//Find one of the flights that was selected from the flight ID
			bookStatement.clearParameters();
			bookStatement.setInt(1, bookFid);
			ResultSet checkResult = bookStatement.executeQuery();

			//If there are no seats available, exit and rollback
			if (!seatsAvailable(checkResult)) {
				System.out.println("There are no seats available on this itinerary.");
				rollbackTransaction();
				checkResult.close();
				return;
			}

			/*
				Make sure no flights are booked on the same day
			 */
			int flightBookDay = checkResult.getInt("day_of_month");
			int flightBookMonth = checkResult.getInt("month_id");
			int flightBookYear = checkResult.getInt("year");

			//Query all the customer's reservations
			reservationListStatement.clearParameters();
			reservationListStatement.setInt(1, cid);
			ResultSet reservationResult = reservationListStatement.executeQuery();

			//loop through reservations
			while (reservationResult.next()) {
				int reservationDay = reservationResult.getInt("day_of_month");
				int reservationMonth = reservationResult.getInt("month_id");
				int reservationYear = reservationResult.getInt("year");
				//Check if the flight customer wants to book is on the same day as this reservation. If so, rollback and exit method.
				if (reservationDay == flightBookDay && reservationMonth == flightBookMonth && reservationYear == flightBookYear) {
					System.out.println("You already have a flight booked on this day.");
					rollbackTransaction();
					checkResult.close();
					reservationResult.close();
					return;
				}
			}

			/*
				Complete the booking
			 */
			//add seat reservation to given flight
			addSeatStatement.clearParameters();
			addSeatStatement.setInt(1, bookFid);
			addSeatStatement.execute();

			//Add flight to customer's reservations
			addReservationStatement.clearParameters();
			addReservationStatement.setInt(1, cid);
			addReservationStatement.setInt(2, bookFid);
			addReservationStatement.execute();

			reservationResult.close();
			checkResult.close();
		}
		System.out.println("Itinerary successfully booked.");
		commitTransaction();
	}

	/*
		If user is logged in, he may view his reservations, which will be printed in order of reservation ID.
		Exits if user isn't logged in
	*/
	public void transaction_reservations() throws Exception {
		//Checks if user is logged in. if not, exit method
		if (cid == -1) {
			System.out.println("You need to be logged in to perform this action.");
			return;
		}

		//Enter parameters for reservation statement
		reservationListStatement.clearParameters();
		reservationListStatement.setInt(1,cid);
		//Execute reservation query
		ResultSet reservationResult = reservationListStatement.executeQuery();

		//List every row of results
		System.out.println("Here are your reservations:");
		while (reservationResult.next()) {
			//Grab and print results
			int reservation_id = reservationResult.getInt("rid");
			String result_day_of_week = reservationResult.getString("day_of_week");
			int result_year = reservationResult.getInt("year");
			String result_month = reservationResult.getString("month");
			int result_dayOfMonth = reservationResult.getInt("day_of_month");
			String result_carrier = reservationResult.getString("carrier");
			String result_flightNum = reservationResult.getString("flight_num");
			String result_originCity = reservationResult.getString("origin_city");
			String result_destinationCity = reservationResult.getString("dest_city");
			int result_time = reservationResult.getInt("actual_time");

			System.out.println("Reservation ID: " + reservation_id + "\t-- Flight date: " + result_day_of_week + " "
					+ result_month + " " + result_dayOfMonth + ", \t" + result_year + "\tCarrier: " + result_carrier
					+ "\tFlight number: " + result_flightNum + "\tDeparture: " + result_originCity + "\tDestination: "
					+ result_destinationCity + "\tFlight time: " + result_time);

		}
		reservationResult.close();
	}

	/*
		If the user is logged in, and knows their reservations, they may cancel a flight reservation
		by indicating the reservation ID. The number of seats booked on that flight will be decreased,
		and the reservation will be removed from the user's reservation list. If the number of seats is already
		at 0 for some reason, then it will stay that way, but the reservation will be removed from the user's list
		Throws IllegalArgumentException (through a private method) if number of seats in the flight is invalid.
		Exits if user isn't logged in, or if reservation ID isn't valid
	*/
	public void transaction_cancel(int reservationId) throws Exception {
		//Check if entered reservation id is valid. if not, exit method
		if (!(reservationId > 0)) {
			throw new IllegalArgumentException("reservationId is invalid");
		}
		//Check if user is logged in. if not, exit method.
		if (cid == -1) {
			System.out.println("You need to be logged in to perform this action.");
			return;
		}

		/*
			Begin the transaction
		*/
		beginTransaction();

		//set parameters to find reservation
		cancelStatement.clearParameters();
		cancelStatement.setInt(1, reservationId);
		cancelStatement.setInt(2, cid);
		//Execute query to find reservation
		ResultSet checkResult = cancelStatement.executeQuery();

		//If the reservation ID was not included in query results, rollback.
		if (!checkResult.next()) {
			System.out.println("Reservation ID not found in your reservations.");
			rollbackTransaction();
			checkResult.close();
			return;
		}

		/*
			Complete cancelation
		*/
		//Check if the flight was already empty. We don't want to subtract 1 from the reservations if it is < 0
		//But we can still remove the customer's reservation.
		if(!seatsEmpty(checkResult)) {
			//Grab flight ID from previous query
			int cancelFid = checkResult.getInt("fid");
			//subtract seats reserved in flight
			removeSeatStatement.clearParameters();
			removeSeatStatement.setInt(1, cancelFid);
			//Execute seat update
			removeSeatStatement.execute();
		}

		//Delete reservation
		deleteReservationStatement.clearParameters();
		deleteReservationStatement.setInt(1, reservationId);
		deleteReservationStatement.setInt(2, cid);
		//Execute deletion
		deleteReservationStatement.execute();

		System.out.println("Reservation ID " + reservationId + " has been removed from your reservations.");
		checkResult.close();
		commitTransaction();
	}

    
	public void beginTransaction() throws Exception {
		conn.setAutoCommit(false);
		beginTransactionStatement.executeUpdate();
	}

	public void commitTransaction() throws Exception {
		commitTransactionStatement.executeUpdate();
		conn.setAutoCommit(true);
	}
	public void rollbackTransaction() throws Exception {
		rollbackTransactionStatement.executeUpdate();
		conn.setAutoCommit(true);
	}

	/*
		Checks whether there are seats available on the flight, in other words if the number of seats reserved is less than
		the maximum capacity of the flight.
		Also check whether the number of seats reserved is illegal.
		Returns a boolean whether there is room on the flight.
	*/
	private boolean seatsAvailable(ResultSet flight) throws Exception {
		int taken = flight.getInt("seats_reserved");
		int max = flight.getInt("max_reservations");
		if (taken > max) {
			throw new IllegalArgumentException("Error: There are too many people booked for this flight.");
		}
		if (taken < 0) {
			throw new IllegalArgumentException("Error: There is an invalid number of people booked for this flight.");
		}
		return taken < max;
	}

	/*
		Checks whether there are no seats taken on the flight.
		Also checks if there is an invalid number of people booked for the flight.
		Returns a boolean whether the flight is empty.
	*/
	private boolean seatsEmpty(ResultSet flight) throws Exception {
		int taken = flight.getInt("seats_reserved");
		if (taken < 0) {
			throw new IllegalArgumentException("Error: There is an invalid number of people booked for this flight.");
		}
		return taken == 0;
	}
}
