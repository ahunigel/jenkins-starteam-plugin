package hudson.plugins.starteam.community;

import hudson.FilePath;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.starteam.Folder;

public class StarTeamConnectionFunctionTest {
	String hostName = System.getProperty("test.starteam.hostname", "starteamserver.ers.na.emersonclimate.org");
	int port = Integer.parseInt(System.getProperty("test.starteam.hostport", "49201")); 
	String agentHost = System.getProperty("test.starteam.agentHost", "CNXA1ER-STARTEA");
  int agentPort = Integer.parseInt(System.getProperty("test.starteam.agentPort", "5201")); 
	String projectName = System.getProperty("test.starteam.projectname", "JARU");
	String viewName = System.getProperty("test.starteam.viewname", "JARU");
	String folderName = System.getProperty("test.starteam.foldername", "JARU/Software/Source/JavaCode");
	String userName = System.getProperty("test.starteam.username", "sonar_hudson_tool");
	String password = System.getProperty("test.starteam.password", "13m3rson");
	final static String CHECHOUT_DIRECTORY = "hudson-temp-directory" ;
	final static String CHANGE_LOG_FILE = "changes.txt";
	StarTeamViewSelector config = null;
	static File parentDirectory = null ;
	
	static File checkoutFolder = null;
	static File changeLogFile = null ;
	
	static File filePointsFile = null;
	
	@BeforeClass
	public static void setUp() throws StarTeamSCMException, IOException {
		System.out.println("Setup test evn");
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
	@Test
	public void testCheckOut() throws StarTeamSCMException, IOException {
		
		try {
			config = new StarTeamViewSelector("", "");
		} catch (ParseException e) {
			Assert.fail("");
		}
		StarTeamConnection connection = new StarTeamConnection(
				hostName, port, agentHost,agentPort,userName, password,
				projectName, viewName, folderName, config);	
		connection.initialize(-1);
		StarTeamChangeSet changeSet;
		
		Folder rootFolder = connection.getRootFolder();
		changeSet = connection.computeChangeSet(rootFolder,checkoutFolder,null,System.out);
		FilePath filePointsFilePath = new FilePath(filePointsFile);
		connection.checkOut(changeSet, System.out, filePointsFilePath);
		
		connection.close();
	}

	@Test
	public void testComputeChangeSet() throws StarTeamSCMException, IOException {
		try {
			config = new StarTeamViewSelector("", "");
		} catch (ParseException e) {
			Assert.fail("");
		}
		StarTeamConnection connection = new StarTeamConnection(
				hostName, port, userName, password,
				projectName, viewName, folderName, config);	
		connection.initialize(-1);
		Folder rootFolder = connection.getRootFolder();
		File filePointFile = new File(parentDirectory, StarTeamConnection.FILE_POINT_FILENAME);
		
		Collection<StarTeamFilePoint> historicFilePoints =null;
		if (filePointFile.exists()) {
			historicFilePoints = StarTeamFilePointFunctions.loadCollection(filePointFile);
		}
		StarTeamChangeSet changeSet;
		changeSet = connection.computeChangeSet(rootFolder,checkoutFolder,historicFilePoints,System.out);
		FilePath filePointsFilePath = new FilePath(filePointsFile);
		connection.checkOut(changeSet, System.out, filePointsFilePath);
		connection.close();
	}

}
