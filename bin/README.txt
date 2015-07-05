

@Shaivi-Kochar

DataBase Name: CouponDunia
Table Name: EmailQueue
Columns: id, from_email_address, to_email_address, subject, body, send_bit.

A java program that will read from this table and send the E-mails over an SMTP server. For the SMTP sending I have used a pre-existing: Javax.mail.jar.

Following points are taken into consideration:
	a) Speed is of taken as the utmost concern. Code uses multi-threading to send one email after another parallely.
	b) Code takes care to ensure that it not sends the same email twice(especially when running multiple processes of the program).
	c) E-mails are not guaranteed to send in the order they are queued.
	h) It asks users for any change in the default configuration of MYSQL_USER, MYSQL_PASSWORD,MYSQL_SERVER,
       DB_NAME, TABLE_NAME.
       
External Libraries Used:

1.	javax.mail-api-1.4.7.jar: JavaMail provides a common, uniform API for managing electronic mail. 
	It allows service-providers to provide a standard interface to their standards-based or
	proprietary messaging systems using the Java programming language. 
	Using this API, applications access message stores, and compose and send messages.
2.	mysql-connector-java-5.1.34-bin.jar: MySQL Connector/J is the official JDBC driver for MySQL.
3. 	activation-jaf1.1.1.jar: The JavaBeans(TM) Activation Framework is used by the JavaMail(TM) API to manage MIME data

       
Classes with there functions:

MailQueue class: Main class that starts the functionalities, Provides SQL related functionalities.

	main() : The main function.
	inputConstraints(): Shows the default configuration and asks user to input new configuration parameters if they want to change the default ones. Returns true/false based on which insertDummyMails() is called or not respectively
	insertDummyMails(): Calls SqlEmailUtil.insertMail() to insert dummy mails in the database. Returns void.
	DistinctMailPairs(): Calls SqlEmailUtil.fetchDistinctEmailPairs() to fetch distinct <to, from> email pairs from the database. Returns vector of String array or null if the execution is successful or not respectively.
	SetConstraints(): Create a new connection to MySQL server, database and table if it does not exists previously.
	insertMail(): Inserts a new row with email informations (from_email_address, to_email_address, subject, body). Returns number of rows inserted or -1 if the execution is successful or not respectively.
	fetchEmails(): Fetches all the e-mails from the database in which from and to email address is equal to the given from and to email address as arguments and sent_bit is set to 0 (the email is not sent earlier). Returns vector of string array or null if the execution is successfull or not repectively.
	fetchDistinctEmailPairs(): Fetches all the distinct <from, to> email pairs from the database. Returns vector of string array or null if the execution is successful or not respectively.
	markSentEmail(): Updates the sent_bit to 1 of all the mail id's (primary key of table) given as an argument as vector of Integers. Returns number of rows updated or -1 if the execution is successful or not respectively.

	
ThreadSend class: Extends Thread class to run threads for sending e-mails, Provides mailing functionality, Holds database information when fetched.

	ThreadSend(): Constructor to set threadName, threadIndex and define vector of integers sendIds.
	run(): Starts the execution of the thread.
	start(): Invokes the thread object to start its execution.
	getEmails(): Calls SqlEmailUtil.fetchEmails() to fetch all the e-mails for a given to and from email address provided as arguments. Returns array of EmailQueue objects having information of all the e-mails.
	sendMails(): Calls Mailing.sendMail() to send all the mails for a given from and to email address. All the e-mails of a given to and from email address are passed as an array of EmailQueue objects. Returns true/false according to the execution is successfull or not respectively.
	Mailing(): Set properties and session object if not set previously.
	sendMail(): Sends all the e-mails provided as an array of EmailQueue objects for a given from and to email address. Returns true/false according to the execution is successful or not respectively.
 	ThreadSend(String values): Constructor to create a new object. Invoked with <from_email_address, to_email_address, subject, body> as arguments or without any argument.
	save(): Calls SqlEmailUtil.insertMail() to save the EmailQueue object as a new row in the database. Returns true/false according to the execution is successful or not respectively.
	

Instructions to run the code:

1.	Run the MailQueue.java and edit the input values on console to set the various options like 
	Smtp host, smto user, smtp password, mysql entries etc. required for proper running of the application.