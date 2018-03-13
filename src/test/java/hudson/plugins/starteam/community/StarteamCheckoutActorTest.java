package hudson.plugins.starteam.community;

import hudson.FilePath;
import hudson.console.ConsoleNote;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.Cause;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Test checkout actor and changelog functionality.
 * @author Steve Favez <sfavez@verisign.com>
 *
 */
public class StarteamCheckoutActorTest {

	final static String CHECHOUT_DIRECTORY = "hudson-temp-directory" ;
	final static String CHANGE_LOG_FILE = "changes.txt" ;
	
	File parentDirectory = null ;
	
	File checkoutFolder = null;
	File changeLogFile = null ;
	
	File filePointsFile = null;
	
	BuildListener listener = new BuildListenerImpl() ;
	/**
	 * initalise integration starteam connection
	 * @throws StarTeamSCMException 
	 * @throws IOException 
	 */
	@Before
	public void setUp() throws StarTeamSCMException, IOException {

		//create the default folder
		parentDirectory = new File(CHECHOUT_DIRECTORY) ;
		if (! parentDirectory.exists()) {
			if (! parentDirectory.mkdir()) {
				Assert.fail( "unable to create the directory" ) ;
			}
		}
		checkoutFolder = new File(parentDirectory, "co");
		if (! checkoutFolder.exists()) {
			if (! checkoutFolder.mkdir()) {
				Assert.fail( "unable to create the directory" ) ;
			}
		}
		changeLogFile = new File( parentDirectory, CHANGE_LOG_FILE ) ;
		if (changeLogFile.exists()) {
			changeLogFile.delete() ;
		}
		if (! changeLogFile.createNewFile() ) {
			Assert.fail( "unable to create changelog file" ) ;
		}
		filePointsFile = new File( parentDirectory,  StarTeamConnection.FILE_POINT_FILENAME) ;
		if (filePointsFile.exists()) {
			filePointsFile.delete() ;
		}
		if (! filePointsFile.createNewFile() ) {
			Assert.fail( "unable to create file point file" ) ;
		}
		
	}
	
	private StarTeamCheckoutActor createStarteamCheckOutActor( Date aPreviousBuildDate, AbstractBuild<?,?> build) {

		String hostName = System.getProperty("test.starteam.hostname", "starteamserver.ers.na.emersonclimate.org");
		int port = Integer.parseInt(System.getProperty("test.starteam.hostport", "49201")); 
		String projectName = System.getProperty("test.starteam.projectname", "JARU");
		String viewName = System.getProperty("test.starteam.viewname", "JARU");
		String folderName = System.getProperty("test.starteam.foldername", "JARU/Software/Scripts");
		String userName = System.getProperty("test.starteam.username", "sonar_hudson_tool");
		String password = System.getProperty("test.starteam.password", "13m3rson");
		
		FilePath changeLogFilePath = new FilePath( changeLogFile ) ;
		FilePath filePointsFilePath = new FilePath(filePointsFile);
		StarTeamViewSelector config = null;
		try {
			config = new StarTeamViewSelector("", "");
		} catch (ParseException e) {
			Assert.fail("");
		}

		return new StarTeamCheckoutActor( hostName, port, "CNXA1ER-STARTEA",5201,userName, password, projectName, viewName, folderName, config, changeLogFilePath, listener, build, filePointsFilePath) ;
	}
	
	@Test
	public void testPerformCheckOut() throws IOException {
		StarTeamCheckoutActor checkoutActor = createStarteamCheckOutActor(null,null) ;
		Boolean res = checkoutActor.invoke( checkoutFolder , null) ;
		Assert.assertTrue( res ) ;
		Assert.assertTrue( changeLogFile.length() > 0 ) ;
		Assert.assertTrue( filePointsFile.length() > 0 ) ;
		checkoutActor.invoke( checkoutFolder , null) ;
	}
	
	@Test
	public void testPerformCheckWithPreviousDateOut() throws IOException {
		Calendar lastYear = Calendar.getInstance() ;
		lastYear.add(Calendar.MONTH, -3) ;
		StarTeamCheckoutActor checkoutActor = createStarteamCheckOutActor(lastYear.getTime(),null) ;
		Boolean res = checkoutActor.invoke( checkoutFolder , null) ;
		Assert.assertTrue( res ) ;
		Assert.assertTrue( changeLogFile.length() > 0 ) ;
		Assert.assertTrue( filePointsFile.length() > 0 ) ;
	}
	
	private final static class BuildListenerImpl implements BuildListener {

		PrintStream printStream ;
		PrintWriter printWriter ;

	    public BuildListenerImpl() {
	    	printStream = System.out ;
	        // unless we auto-flash, PrintStream will use BufferedOutputStream internally,
	        // and break ordering
	        this.printWriter = new PrintWriter(new BufferedWriter(
	                 new OutputStreamWriter(printStream) ), true);
	    }

	    public void started(List<Cause> causes) {
	        if (causes==null || causes.isEmpty())
	        	printStream.println("Started");
	        else for (Cause cause : causes) {
	        	printStream.println(cause.getShortDescription());
	        }
	    }

	    public PrintStream getLogger() {
	        return printStream;
	    }

	    public PrintWriter error(String msg) {
	    	printWriter.println("ERROR: "+msg);
	        return printWriter;
	    }

	    public PrintWriter error(String format, Object... args) {
	        return error(String.format(format,args));
	    }

	    public PrintWriter fatalError(String msg) {
	    	printWriter.println("FATAL: "+msg);
	        return printWriter;
	    }

	    public PrintWriter fatalError(String format, Object... args) {
	        return fatalError(String.format(format,args));
	    }

	    public void finished(Result result) {
	    	printWriter.println("Finished: "+result);
	    }

		@Override
		public void annotate(ConsoleNote ann) throws IOException {
			// TODO: 3/12/2018 implement annotate method
		}

		@Override
		public void hyperlink(String url, String text) throws IOException {
			// TODO: 3/12/2018 implement hyperlink method
		}
	}
	
}
