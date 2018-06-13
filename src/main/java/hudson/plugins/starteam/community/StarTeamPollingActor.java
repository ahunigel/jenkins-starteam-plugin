/**
 *
 */
package hudson.plugins.starteam.community;

import com.google.common.base.Strings;
import hudson.FilePath.FileCallable;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * This Actor class allow to check for changes in starteam repository between
 * builds.
 *
 * @author Ilkka Laukkanen <ilkka.s.laukkanen@gmail.com>
 * @author Steve Favez <sfavez@verisign.com>
 */
public class StarTeamPollingActor implements FileCallable<Boolean> {

  /**
   * serial version id.
   */
  private static final long serialVersionUID = -5678102033953507247L;

  private String hostname;

  private int port;

  private final String agenthost;

  private final int agentport;

  private String user;

  private String passwd;

  private String projectname;

  private String viewname;

  private String foldername;

  private String subfolder;

  private final TaskListener listener;

  private final StarTeamViewSelector config;

  private Collection<StarTeamFilePoint> historicFilePoints;

  /**
   * Default constructor.
   *
   * @param hostname           starteam host name
   * @param port               starteam port
   * @param user               starteam connection user name
   * @param passwd             starteam connection password
   * @param projectname        starteam project name
   * @param viewname           starteam view name
   * @param foldername         starteam parent folder name
   * @param subfolder
   * @param config             configuration selector
   * @param listener           Hudson task listener.
   * @param historicFilePoints
   */
  public StarTeamPollingActor(String hostname, int port, String agentHost, int agentPort, String user,
                              String passwd, String projectname, String viewname,
                              String foldername, String subfolder, StarTeamViewSelector config, TaskListener listener,
                              Collection<StarTeamFilePoint> historicFilePoints) {
    this.hostname = hostname;
    this.port = port;
    this.agenthost = agentHost;
    this.agentport = agentPort;
    this.user = user;
    this.passwd = passwd;
    this.projectname = projectname;
    this.viewname = viewname;
    this.foldername = foldername;
    this.listener = listener;
    this.subfolder = subfolder;
    this.config = config;
    this.historicFilePoints = historicFilePoints;
  }

  /*
   * (non-Javadoc)
   *
   * @see hudson.FilePath.FileCallable#invoke(java.io.File,
   *      hudson.remoting.VirtualChannel)
   */
  public Boolean invoke(File f, VirtualChannel channel) throws IOException {

    StarTeamConnection connection = new StarTeamConnection(
        hostname, port, agenthost, agentport, user, passwd,
        projectname, viewname, foldername, config, false);
    try {
      connection.initialize(-1);
    } catch (StarTeamSCMException e) {
      listener.getLogger().println(e.getLocalizedMessage());
      connection.close();
      return false;
    }

    StarTeamChangeSet changeSet = null;
    File workFolder = Strings.isNullOrEmpty(subfolder) ? f : new File(f, subfolder.trim());
    try {
      changeSet = connection.computeChangeSet(connection.getRootFolder(), workFolder, historicFilePoints, listener.getLogger());
    } catch (Exception e) {
      e.printStackTrace(listener.getLogger());
    }
    connection.close();
    return changeSet != null && changeSet.hasChanges();
  }

  @Override
  public void checkRoles(RoleChecker roleChecker) throws SecurityException {

  }
}
