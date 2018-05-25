package hudson.plugins.starteam.community;

import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;

import java.nio.charset.Charset;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;
@Ignore
public class StarTeamSCM1Test extends HudsonTestCase
{
  private static final int LOG_LIMIT = 1000;
  
  StarTeamSCM t;
  String hostName = System.getProperty("test.starteam.hostname", "starteamserver.ers.na.emersonclimate.org");
  int port = Integer.parseInt(System.getProperty("test.starteam.hostport", "49201")) ; 
  String projectName = System.getProperty("test.starteam.projectname", "JARU");
//  String viewName = System.getProperty("test.starteam.viewname", "12.1.3 Branch");
  String viewName = System.getProperty("test.starteam.viewname", "JARU");
  String folderName = System.getProperty("test.starteam.foldername", "JARU/Software");
  String userName = System.getProperty("test.starteam.username", "mzhu");
  String password = System.getProperty("test.starteam.password", "mickey");
  String labelName = System.getProperty("test.starteam.labelname", "");
  String promotionName = System.getProperty("test.starteam.promotionname", "");
  String changeDate = System.getProperty("test.starteam.changedate", "");
  String testFile = System.getProperty("test.starteam.testfile", "");
  String cacheagenthost= System.getProperty("test.starteam.cacheagenthost", "CNXA1ER-STARTEA");
  int cacheagentport = Integer.parseInt(System.getProperty("test.starteam.cacheagentport", "5201"));
  
  @Before
  public void setUp() throws Exception {
    super.setUp();
    t = new StarTeamSCM(hostName, port, projectName, viewName, folderName, userName, password, null, false, cacheagenthost, cacheagentport, true) ;
  }
    
  @After  
  public void tearDown() throws Exception  {  
     super.tearDown();
  }

  @Test
  public void testPollChange()throws Exception
  {
    FreeStyleProject project = createFreeStyleProject();
    project.setCustomWorkspace("D:/Hudson Builds/Jaru/Software");
    project.setScm(t);

    final TaskListener listener = new StreamBuildListener (System.out,Charset.forName("UTF-8"));

    FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserCause()).get();

    System.out.println(build.getLog(LOG_LIMIT));
    assertBuildStatus(Result.SUCCESS,build);
    FreeStyleBuild lastBuild = project.getLastBuild();
    assertNotNull(lastBuild);

    // polling right after a build should not find any changes.
    boolean result = project.pollSCMChanges(listener);

    assertFalse(result);
  }

}
