package coupondunia;
import java.sql.SQLException;
import java.util.Vector;
import javax.mail.MessagingException;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/*
 * @Shaivi-Kochar 
Table Name: EmailQueue
Columns: id, from_email_address, to_email_address, subject, body, send_bit.

A java program that will read from this table and send the E-mails over an SMTP server. For the SMTP sending I have used a pre-existing: Javax.mail.jar.

Following points are taken into consideration:
	a) Speed is of taken as the utmost concern. Code uses multi-threading to send one email after another parallely.
	b) Code takes care to ensure that it not sends the same email twice(especially when running multiple processes of the program).
	c) E-mails are not guaranteed to send in the order they are queued.
	h) It asks users for any change in the default configuration, like MYSQL_USER, MYSQL_PASSWORD,MYSQL_SERVER,
       DB_NAME, TABLE_NAME .
 * 
 */

public class ThreadSend extends Thread 
{
    private Thread t = null;
    private String threadName;      						//Name of the thread
    private int threadIndex;        						//Number of the thread
    private Vector<Integer> sent_ids;						//send bits for every mail
    public static String SMTP_HOST = "localhost",    		//The SMTP host address
    					 SMTP_USER = "shaivi@localhost.com",   //The SMPTP User name/email
    					 SMTP_PASSWORD = "shaivi";          	//SMTP password     
	private static Properties properties = null;             	//The properties for the session
	private static Session sessionObj = null;              	//The session object
	private String from, to, subject, body;
    private int id;
    private static ThreadSend[] mails = null;
	
	
    //Default Constructor
	public ThreadSend() 
	{        
	}
	//Parametrised Constructor_1
    ThreadSend(int num)
    {
        this.threadIndex = num;
        this.threadName = "Thread_"+num;
        sent_ids = new Vector<Integer>();
        System.out.println("Creating thread " +  threadName );
    }
    //Parametrised Construction_2
    public ThreadSend(String from_1, String to_1, String sub_1, String body_1) 
    {
    	this.from = from_1;
        this.to = to_1;
        this.subject = sub_1;
        this.body = body_1;
    }
    
    //saves the object as a new row in the database
    public boolean save() 
    {
    	MailQueue email = new MailQueue();
        int rows_affected = email.insertMail(this.from, this.to, this.subject, this.body);      //uses insertMail() utility function in SqlEmailUtility class
        if(rows_affected == -1) {
            return false;
        } else {
            return true;
        }
    }
   
    @Override
    public void run() {
        System.out.println("Running " +  threadName );
        try {
            //Each threads fetches emails of the email pairs occuring at a distance of NUM_THREADS to prevent 
            //multiple sending of emails as well as preventing any email not being sent
            
            for(int i=this.threadIndex-1 ; i<MailQueue.mailPairs.size(); i += MailQueue.NUM_THREADS) 
            {    
                String[] str = MailQueue.mailPairs.get(i);
                mails = this.getEmails(str[0], str[1]);   //gets all the emails for the email pair 
                if(mails.length > 0) {
                    String from = mails[0].from;
                    String to = mails[0].to;

                    if(this.sendMails(from, to)) {          //passes params to send all the 'emails' for the given to and from 
                        System.out.println(
                                this.threadName + ": " 
                                + mails.length + " emails from "
                                + from + " email to " 
                                + to + " email is sent");
                        
                    } else {
                        System.out.println(
                                this.threadName + ": Could not send "
                                + mails.length + "the emails from "
                                + from + " email to " 
                                + to + " email");
                    }
                }
            }
            try {
                new MailQueue().markSentEmail(this.sent_ids);     //after each threads finishes sending mails, they update the sent_bit = 1
                MailQueue.endTime = System.currentTimeMillis();

                System.out.println("That took " + (MailQueue.endTime - MailQueue.startTime) + " milliseconds");
            
            } catch (SQLException e) {
                System.out.println("Thread: "+this.threadName + "could not be update for sent_bit of email having ID");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error: " +  threadName + " - " + e.getMessage());
        }
        System.out.println("Thread: " +  threadName + " already in process.");
    }

    @Override
    public void start () {          //starts the thread's execution
        System.out.println("Starting new Thread:" +  threadName );
        if (t == null) {
            t = new Thread(this, threadName);
            t.start ();
        }
    }
    
    public ThreadSend[] getEmails(String from, String to) 
    {
        Vector<String[]> mailRows;
        MailQueue obj = new MailQueue();
        try {
            mailRows = obj.fetchEmails(from, to);   //fetches all the emails for the given from, to email pairs using utility functions of SqlEmailUtil class
            
            mails = new ThreadSend[mailRows.size()];   //array of information in objects for storing all the details of records returned fetched from database
            for(int i=0; i<mailRows.size(); i++) 
            {
                String[] str = mailRows.get(i);

                mails[i] = new ThreadSend();
                mails[i].id = Integer.parseInt(str[0]);
                mails[i].from = str[1];
                mails[i].to = str[2];
                mails[i].subject = str[3];
                mails[i].body = str[4];
            }
            return mails;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Could not fetch emails: " + e.getMessage());
            return null;
        }

    }
    
    public boolean sendMails(String from, String to) {
        
        try {
            sendMail(from, to, this);       //appends ThreadSend object so that the thread could be identified from that point
            
            return true;
        } catch (MessagingException ex) {
            ex.printStackTrace();
            System.out.println("Could not send emails " + ex.getMessage());
            return false;
        }
    }
	//set the Session object to store information needed for a particular email session from the database. 
	public void Mailing() 
	{
		if(properties == null) 
		{         
			//Set values to the properties object if it is not already been set earlier
			properties = new Properties();
			properties.put("mail.smtp.auth", "true");
			properties.put("mail.smtp.starttls.enable", "true");
			properties.put("mail.smtp.host", SMTP_HOST);
			properties.put("mail.smtp.port", "25");
	
		}
	
		if(sessionObj == null) {       //Creates a new session object only if it is not been created earlier
			sessionObj = Session.getInstance(properties, new javax.mail.Authenticator() {
			    @Override
			    protected PasswordAuthentication getPasswordAuthentication() {
			        return new PasswordAuthentication(SMTP_USER, SMTP_PASSWORD);
			    }
			 });
		}
	}
	//Sends the mail, the thread responsible for sending this mail is represented by "th" object
	public boolean sendMail(String from, String to, ThreadSend th) throws MessagingException 
	{
		Mailing();
		try 
		{
			Message msg = new MimeMessage(sessionObj);
			
			//Since all the messages have same from and to fields, it is set only once
			
			msg.setFrom(new InternetAddress(from));
			msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
			
			//all the mails are then sent one by one which has the same from and to email pairs
			for(int i=0; i<mails.length; i++) {
				msg.setSubject(mails[i].subject);
				msg.setText(mails[i].body);
				
				Transport.send(msg);
				th.sent_ids.addElement(mails[i].id);
			}
		return true;
		} 
		catch (MessagingException e) 
		{
			throw e;
		}
	}
}