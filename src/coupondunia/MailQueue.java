package coupondunia;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Vector;

/*
Classes with there functions:

MailQueue class : Main class which kick starts the functionalities, Provides SQL related functionalities.

	main() : The main function.
	inputConstraints(): Shows the default configuration and asks user to input new configuration parameters if they want to change the default ones. Returns true/false based on which insertDummyMails() is called or not respectively
	insertDummyMails(): Calls SqlEmailUtil.insertMail() to insert dummy mails in the database. Returns void.
	DistinctMailPairs(): Calls SqlEmailUtil.fetchDistinctEmailPairs() to fetch distinct <to, from> email pairs from the database. Returns vector of String array or null if the execution is successful or not respectively.
	SetConstraints(): Create a new connection to MySQL server, database and table if it does not exists previously.
	insertMail(): Inserts a new row with email informations (from_email_address, to_email_address, subject, body). Returns number of rows inserted or -1 if the execution is successful or not respectively.
	fetchEmails(): Fetches all the e-mails from the database in which from and to email address is equal to the given from and to email address as arguments and sent_bit is set to 0 (the email is not sent earlier). Returns vector of string array or null if the execution is successfull or not repectively.
	fetchDistinctEmailPairs(): Fetches all the distinct <from, to> email pairs from the database. Returns vector of string array or null if the execution is successful or not respectively.
	markSentEmail(): Updates the sent_bit to 1 of all the mail id's (primary key of table) given as an argument as vector of Integers. Returns number of rows updated or -1 if the execution is successful or not respectively.

	
ThreadSend class (extends Thread): Extends Thread class to run threads for sending e-mails, Provides mailing functionality, Holds database information when fetched.

	ThreadSend(): Constructor to set threadName, threadIndex and define vector of integers send_ids.
	run(): Starts the execution of the thread.
	start(): Invokes the thread object to start its execution.
	getEmails(): Calls SqlEmailUtil.fetchEmails() to fetch all the e-mails for a given to and from email address provided as arguments. Returns array of EmailQueue objects having information of all the e-mails.
	sendMails(): Calls Mailing.sendMail() to send all the mails for a given from and to email address. All the e-mails of a given to and from email address are passed as an array of EmailQueue objects. Returns true/false according to the execution is successfull or not respectively.
	Mailing(): Set properties and session object if not set previously.
	sendMail(): Sends all the e-mails provided as an array of EmailQueue objects for a given from and to email address. Returns true/false according to the execution is successful or not respectively.
 	ThreadSend(String values): Constructor to create a new object. Invoked with <from_email_address, to_email_address, subject, body> as arguments or without any argument.
	save(): Calls SqlEmailUtil.insertMail() to save the EmailQueue object as a new row in the database. Returns true/false according to the execution is successful or not respectively.

*/



/* 
Technique Used: 
	1. Main program fetches all E-mail address from the default database "CouponDuniya" with table name "EmailQueue" and stores in MailQueue.mailPairs 
	   vector of string arrays.
	2. Now MailQueue.NUM_THREADS is number of threads that are created with threadIndex from 1 to MailQueue.NUM_THREADS and their 
	   subsequent threadName is stored as "Thread X" where X is the threadIndex of that thread.
	3. Each thread sends E-mails of E-mail pairs which are on indices i + MailQueue.NUM_THREADS where 
	   threadIndex <= i <= (size(mailPairs) - threadIndex).
	4. It stores the "id" (Primary key) of the E-mail present in the database in each of their sentIds integer vectors after the mail is send by a thread.
	5. As soon as all the E-mails of a particular E-mail address pair are sent, the sentIds are passed to markSentEmail function 
	   which then updates the sent_bit column from '0' to '1' to ensure that the particular E-mail(s) would not be sent again in the future.
*/
public class MailQueue extends ThreadSend{
									
    public static Vector<String[]> mailPairs = null;        		//stores the distinct <from, to> mail pairs from the database
    public static int NUM_THREADS = 2;
    private static int MYSQL_PORT = 3306;    						//Default Mysql port number
    private static String MYSQL_USER = "root",      					//Default Mysql user
                        MYSQL_PASSWORD = "a",     					//Default Mysql user password
                        MYSQL_SERVER = "localhost",
                        HOST = "jdbc:mysql://localhost:3306/",      //Default Mysql Host address
                        DB_NAME = "CouponDunia",        			//Default Database name
                        DB_URL = HOST + DB_NAME,        			//Default Database connection url with database param
                        JDBC_DRIVER = "com.mysql.jdbc.Driver",      //JDBC driver name
                        TABLE_NAME = "EmailQueue";      			//Default Table name
    private static Connection conn = null;
    private static boolean dbCreated = false;
	private static boolean tableCreated = false;
	public static long endTime,startTime;
	
	//check for the database and table.
    private static void SetConstraints() 
    {
    	
    	if(conn == null) {
            try {
                Class.forName(JDBC_DRIVER);
                //first we connect using HOST as we are not sure about the presence of CouponDunia database
                conn = DriverManager.getConnection(HOST, MYSQL_USER, MYSQL_PASSWORD);

                Statement s = conn.createStatement();
                //Create database and table if it is not there previously
                s.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DB_NAME);
                dbCreated = true;

            } catch(ClassNotFoundException e) {
                System.out.println("Error: Class " + JDBC_DRIVER +" not found");
            } catch(SQLException e) {
                System.out.println("here: "+ e.getMessage());
            } finally {
                if(dbCreated == true) {     //this means the database is created, so we set conn to connect to DB_URL rather than HOST
                    try {
                    	conn.close();
                    	conn = DriverManager.getConnection(DB_URL, MYSQL_USER, MYSQL_PASSWORD);
                        
                        if(tableCreated == false) {
                            Statement s = conn.createStatement();
                            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                                + "  `id` int(11) NOT NULL AUTO_INCREMENT,"
                                + "  `from_email_address` varchar(100) NOT NULL,"
                                + "  `to_email_address` varchar(100) NOT NULL,"
                                + "  `subject` varchar(200) NOT NULL,"
                                + "  `body` varchar(1000) NOT NULL,"
                                + "  `sent_bit` int(11) NOT NULL DEFAULT '0',"
                                + "  PRIMARY KEY (`id`)) ENGINE=InnoDB  DEFAULT CHARSET=latin1;");
                            
                            tableCreated = true;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    } 									
	//set the default configuration for setting the mail sending system through smtp. Also changes the values acc to user needs.
    private static boolean inputConstraints() throws IOException 
    {
        InputStreamReader istream = new InputStreamReader(System.in) ;
        BufferedReader inp = new BufferedReader(istream) ;
        
        System.out.println("The default configuration is: ");				
        System.out.println("Number of threads: "+NUM_THREADS);
        
        System.out.println("Mysql user: " + MYSQL_USER);
        System.out.println("Mysql "+MYSQL_USER+" password: "+ MYSQL_PASSWORD);
        System.out.println("Mysql Server address: " + MYSQL_SERVER);
        System.out.println("Mysql Database Name: " + DB_NAME);
        System.out.println("Mysql table name: "+ TABLE_NAME);
        System.out.println("Mysql table name: "+ TABLE_NAME);
        
        System.out.println("SMTP Server address: "+ ThreadSend.SMTP_HOST);
        System.out.println("SMTP user email: "+ ThreadSend.SMTP_USER);
        System.out.println("SMTP user password: "+ ThreadSend.SMTP_PASSWORD);
        
        System.out.println("\n");
        System.out.println("Enter 'exit' in case you want to exit this. ");
        String choice;
        while(true) 
        {
            System.out.print("Enter 'Y' if you want to change reconfigure, else 'N'. ");
            choice = inp.readLine();
            if("exit".equals(choice)) {
                System.exit(0);
            } 
            else if("Y".equals(choice)) {
                System.out.print("Enter the number of threads you want to use: ");
                NUM_THREADS = Integer.parseInt(inp.readLine());

                System.out.print("Enter MySQL user name: ");
                MYSQL_USER = inp.readLine();
                System.out.print("Enter MySQL user password: ");
                MYSQL_PASSWORD = inp.readLine();
                System.out.print("Enter MySQL server address: ");
                MYSQL_SERVER = inp.readLine();
                System.out.print("Enter MySQL database name: ");
                DB_NAME = inp.readLine();
                System.out.print("Enter the table in which mails are kept: ");
                TABLE_NAME = inp.readLine();
                
                System.out.print("Enter the SMTP server address: ");
                ThreadSend.SMTP_HOST = inp.readLine();
                System.out.print("Enter the SMTP user email: ");
                ThreadSend.SMTP_USER = inp.readLine();
                System.out.print("Enter the SMTP user password: ");
                ThreadSend.SMTP_PASSWORD = inp.readLine();
                
                HOST = "jdbc:mysql://"+MYSQL_SERVER+":"+MYSQL_PORT+"/";
                DB_URL = HOST + DB_NAME;
                
                break;
            } 
            else if("N".equals(choice)) {
                break;
            } 
            else {
                System.out.println("Wrong choice...Please enter again");
            }
        }
        
        System.out.println("By default, no data would be inserted into the table");
        System.out.print("Would you like to insert dummy data (Y/N): ");
        choice = inp.readLine();
        
        if("exit".equals(choice)) {
            System.exit(0);
            return false;
        } 
        else if("Y".equals(choice)) {
            return true;
        } 
        else {
            return false;
        }
    }
    
  //Inserts an email record
    public int insertMail(String from, String to, String subject, String body) {
        if(conn == null) {
            return -1;
        } else {
            Statement stmt = null;
            try {
                stmt = conn.createStatement();
            
                String sql = "INSERT INTO " + TABLE_NAME 
                        + "(from_email_address, to_email_address, subject, body)"
                        + "VALUES ('"+ from +"', '"+ to +"', '"+ subject + "', '"+ body +"')";
                
                int num_rows_affected = stmt.executeUpdate(sql);
                
                return num_rows_affected;
                
            } catch (Exception e) {
                e.printStackTrace();
                return -1;
            } finally {
                try {
                    stmt.close();
                } catch(Exception e) {
                    e.printStackTrace();
                    return -1;
                }
            }
        }
    }
    
    //fetch all the e-mails for the given from and to email addresses
    public Vector<String[]> fetchEmails(String from, String to) throws Exception 
	{
        if(conn == null) 
		{
            return null;
        } 
		else 
		{
            Vector<String[]> results = new Vector<String[]>();
            
            Statement stmt = null;
            ResultSet rs = null;
            try 
			{
                stmt = conn.createStatement();
                String sql = "SELECT id, from_email_address, to_email_address, subject, body FROM " 
                        + TABLE_NAME + " WHERE from_email_address = '" 
                        + from + "' AND to_email_address = '" 
                        + to + "' AND sent_bit = 0 ORDER BY id";
                
                rs = stmt.executeQuery(sql);
                while(rs.next()) {
                    String[] row = new String[5];

                    row[0] = ""+rs.getInt("id");
                    row[1] = rs.getString("from_email_address");
                    row[2] = rs.getString("to_email_address");
                    row[3] = rs.getString("subject");
                    row[4] = rs.getString("body");

                    results.add(row);
                }
            } 
			catch (Exception e) 
			{
                e.printStackTrace();
                throw e;
            } 
			finally 
			{
                rs.close();
                stmt.close();
            }
            return results;
        }
    }
    
    //Fetches all mails from and to email pairs from the DB
    public static Vector<String[]> fetchAllEmail() throws Exception
	{
        if(conn == null) 
		{
            return null;
        } 
		else 
		{
            Vector<String[]> results = new Vector<String[]>();
            ResultSet rs = null;
            Statement stmt = null;
            try 
			{
                stmt = conn.createStatement();
                String sql = "SELECT from_email_address, to_email_address FROM EmailQueue";
                rs = stmt.executeQuery(sql);
                
                while(rs.next()) {
                    String[] row = {
                        rs.getString("from_email_address"),
                        rs.getString("to_email_address")
                    };

                    results.add(row);
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            } finally {
                rs.close();
                stmt.close();
            }
            return results;
        }
    }
    
    
    //Sets the sent_bit to 1 of all e-mails that are sent
    public int markSentEmail(Vector<Integer> ids) throws SQLException 
	{
        if(conn == null) 
		{
            return -1;
        } 
		else 
		{
            int rows_updated = 0;   						//Total number of rows updated.
            Statement stmt = null;
            try 
			{
                stmt = conn.createStatement();
                System.out.println("---------------" + ids.size() + " Records Updated----------------");
                String idList = "(";
                for(int i=0; i<ids.size()-1; i++) {
                    idList += ids.get(i);
                    idList += ",";
                }
                idList += ids.get(ids.size()-1);
                idList += ")";
                String sql = "UPDATE EmailQueue SET sent_bit = 1 WHERE id IN " + idList;
                rows_updated = stmt.executeUpdate(sql);
            } 
			catch (SQLException e) 
			{
                e.printStackTrace();
                throw e;
            } 
			finally 
			{
                stmt.close();
            }
            return rows_updated;
        }
    }
    
    private static void insertDummyMails() {
    	ThreadSend newRow;
        for(int i=0; i<10; i++) {
            for(int j=0; j<5; j++) {
                newRow = new ThreadSend("abc@gma.com"+i, "cd@gmail.com"+j, "Interview Round 2"+ (i*5+j), "Body Message"+(i*5+j));
                newRow.save();
            }
        }
        
    }
    
    //gets all <from, to> mail pairs from the database
    private static Vector<String[]> MailPairs()
    {
        Vector<String[]> pairRows = null;
        try 
        {
        	pairRows = fetchAllEmail();
        	return pairRows;
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            System.out.println("Could not fetch email pairs: " + e.getMessage());
            return null;
        }
    }
    
    
    public static void main(String[] args) throws IOException
    {
    	
    	SetConstraints();
        boolean insertFlag = inputConstraints();        	//for reconfiguring the settings
															//check if user wanted to add dummy mails into database table.
        if(insertFlag == true) 
        {
            insertDummyMails();
        }
        startTime = System.currentTimeMillis();

        mailPairs = MailPairs();     						//gets the distinct (from_email_address, to_email_address) pairs from DB
        if(mailPairs != null ) 
		{													//Creating threads to handle fast email sending
            ThreadSend t;
            for(int i=1; i<=NUM_THREADS; i++) {
                t = new ThreadSend(i);
                t.start();
            }
        }
    }
}