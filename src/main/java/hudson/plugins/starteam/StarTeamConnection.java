/**
 *
 */
package hudson.plugins.starteam;

import com.starteam.*;
import com.starteam.File;
import com.starteam.File.EOLFormat;
import com.starteam.events.CheckoutEvent;
import com.starteam.events.CheckoutListener;
import com.starteam.exceptions.DuplicateServerListEntryException;
import com.starteam.exceptions.LogonException;
import com.starteam.util.DateTime;
import com.starteam.util.MD5;
import hudson.FilePath;

import java.io.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * StarTeamActor is a class that implements connecting to a StarTeam repository,
 * to a given project, view and folder.
 * <p>
 * Add functionality allowing to delete non starteam file in folder while
 * performing listing of all files. and to perform creation of changelog file
 * during the checkout
 *
 * @author Ilkka Laukkanen <ilkka.s.laukkanen@gmail.com>
 * @author Steve Favez <sfavez@verisign.com>
 */
public class StarTeamConnection implements Serializable {
  private static final long serialVersionUID = 1L;

  public static final String FILE_POINT_FILENAME = "starteam-filepoints.csv";
  private SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm:ss");
  private final String hostName;
  private final int port;
  private final String userName;
  private final String password;
  private final String projectName;
  private final String viewName;
  private final String folderName;
  private final String agentHost;
  private final int agentPort;
  private final StarTeamViewSelector configSelector;

  private transient Server server;
  private transient View view;
  private transient Folder rootFolder;
  private transient Project project;
  private transient boolean canReadUserAccts = true;

  static {
    try {
      String netmonFile = System.getenv("st.netmon.out");
      if (netmonFile != null) {
        NetMonitor.onFile(new java.io.File(netmonFile));
      }
    } catch (Throwable t) { // catch throwable to make sure the class loads, logging isn't essential
      try {
        System.err.println("Can't write StarTeam network monitor logs to netmon.out: " + t);
      } catch (Throwable ignore) {
      }
    }
  }

  /**
   * Default constructor
   *
   * @param hostName       the starteam server host / ip name
   * @param port           starteam server port
   * @param userName       user used to connect starteam server
   * @param password       user password to connect to starteam server
   * @param projectName    starteam project's name
   * @param viewName       starteam view's name
   * @param folderName     starteam folder's name
   * @param configSelector configuration selector in case of checking from label, promotion
   *                       state or time
   */
  public StarTeamConnection(String hostName, int port, String userName, String password, String projectName,
                            String viewName, String folderName, StarTeamViewSelector configSelector) {
    this(hostName, port, null, -1, userName, password, projectName, viewName, folderName, configSelector);
  }

  public StarTeamConnection(String hostName, int port, String agentHost, int agentPort, String userName,
                            String password, String projectName, String viewName, String folderName, StarTeamViewSelector configSelector) {
    checkParameters(hostName, port, userName, password, projectName, viewName, folderName);
    this.hostName = hostName;
    this.port = port;
    this.userName = userName;
    this.password = password;
    this.projectName = projectName;
    this.viewName = viewName;
    this.folderName = folderName;
    this.configSelector = configSelector;
    this.agentHost = agentHost;
    this.agentPort = agentPort;
  }

  public StarTeamConnection(StarTeamConnection oldConnection, StarTeamViewSelector configSelector) {
    this(oldConnection.hostName, oldConnection.port, oldConnection.userName, oldConnection.password,
        oldConnection.projectName, oldConnection.viewName, oldConnection.folderName, configSelector);
  }

  private ServerInfo createServerInfo() {
    ServerInfo serverInfo = new ServerInfo();
    serverInfo.setConnectionType(ServerConfiguration.PROTOCOL_TCP_IP_SOCKETS);
    serverInfo.setHost(this.hostName);
    serverInfo.setPort(this.port);
    if (this.agentHost != null) {
      serverInfo.setMPXCacheAgentAddress(agentHost);
      serverInfo.setMPXCacheAgentPort(agentPort);
      serverInfo.setMPXCacheAgentThreadCount(2);
      serverInfo.setEnableCacheAgentForFileContent(true);
    }
    populateDescription(serverInfo);

    return serverInfo;
  }

  /**
   * populate the description of the server info.
   *
   * @param serverInfo
   */
  void populateDescription(ServerInfo serverInfo) {
    // Increment a counter until the description is unique
    int counter = 0;
    while (!setDescription(serverInfo, counter))
      ++counter;
  }

  private boolean setDescription(ServerInfo serverInfo, int counter) {
    try {
      serverInfo.setDescription("StarTeam connection to " + this.hostName
          + ((counter == 0) ? "" : " (" + Integer.toString(counter) + ")"));
      return true;
    } catch (DuplicateServerListEntryException e) {
      return false;
    }
  }

  private void checkParameters(String hostName, int port, String userName, String password, String projectName,
                               String viewName, String folderName) {
    if (null == hostName)
      throw new NullPointerException("hostName cannot be null");
    if (null == userName)
      throw new NullPointerException("user cannot be null");
    if (null == password)
      throw new NullPointerException("passwd cannot be null");
    if (null == projectName)
      throw new NullPointerException("projectName cannot be null");
    if (null == viewName)
      throw new NullPointerException("viewName cannot be null");
    if (null == folderName)
      throw new NullPointerException("folderName cannot be null");

    if ((port < 1) || (port > 65535))
      throw new IllegalArgumentException("Invalid port: " + port);
  }

  /**
   * Initialize the connection. This means logging on to the server and finding
   * the project, view and folder we want.
   *
   * @param buildNumber a job build number, or -1 if not associated with a job.
   * @throws StarTeamSCMException if logging on fails.
   */
  public void initialize(int buildNumber) throws StarTeamSCMException {
    /*
     * Identify this as the StarTeam Hudson Plugin so that it can support the
     * new AppControl capability in StarTeam 2009 which allows a StarTeam
     * administrator to block or allow Unknown or specific client/SDK
     * applications from accessing the repository; without this, the plugin will
     * be seen as an Unknown Client, and may be blocked by StarTeam repositories
     * that take advantage of this feature. This must be called before a
     * connection to the server is established.
     */
    // Application.setName("StarTeam Plugin for Jenkins");

    server = new Server(createServerInfo());
    server.connect();
    try {
      server.logOn(userName, password);
    } catch (LogonException e) {
      throw new StarTeamSCMException("Could not log on: " + e.getErrorMessage());
    }
    if (server.isMPXAvailable()) {
      CacheAgent agent = server.locateCacheAgent(agentHost, agentPort);
    }
    project = findProjectOnServer(server, projectName);
    view = findViewInProject(project, viewName);
    if (configSelector != null) {
      View configuredView = null;
      try {
        configuredView = configSelector.configView(view, buildNumber);
      } catch (ParseException e) {
        throw new StarTeamSCMException("Could not correctly parse configuration date: " + e.getMessage());
      }
      if (configuredView != null)
        view = configuredView;
    }
    rootFolder = StarTeamFunctions.findFolderInView(view, folderName);

    rootFolder.populate(server.getTypes().FILE, -1);
    // rootFolder.populate(server.getTypes().FILE, filePropertyCollection, -1);
    rootFolder.populate(server.getTypes().FOLDER, -1);

  }

  /**
   * checkout the files from starteam
   *
   * @param changeSet         a description of changes
   * @param filePointFilePath A FilePath reprensenting the file points file where to store the
   *                          change set
   * @throws IOException if checkout fails.
   */
  public void checkOut(StarTeamChangeSet changeSet, final PrintStream logger, FilePath filePointFilePath)
      throws IOException {
    long startTime = System.currentTimeMillis();

    logger.println("*** " + sdf.format(new Date()) + " Performing checkout on [" + changeSet.getFilesToCheckout().size() + "] files");

    List<File> filesToCheckout = new ArrayList<File>();

    filesToCheckout.addAll(changeSet.getFilesToCheckout());
    boolean quietCheckout = filesToCheckout.size() >= 2000;
    if (quietCheckout) {
      logger.println("*** " + sdf.format(new Date()) + "  More than 2000 files, quiet mode enabled");
    }
    com.starteam.CheckoutOptions coOptions = new com.starteam.CheckoutOptions(view);
    coOptions.setLockType(Item.LockType.UNLOCKED);
    coOptions.setEOLFormat(EOLFormat.PLATFORM);
    coOptions.setUpdateStatus(true);
    coOptions.setTimeStampNow(false);
    coOptions.setForceCheckout(true);
    coOptions.setMarkUnlockedFilesReadOnly(false);
    CheckoutManager com = view.createCheckoutManager(coOptions);
    if (com.getView().getProject().getServer().getServerInfo().getEnableCacheAgentForFileContent()) {
      logger.println("*** " + sdf.format(new Date()) + " Enabled cache agent for file content.");
    }
    CheckoutListenerImpl colistener = new CheckoutListenerImpl(logger);
    colistener.setUpdateLastModifyDate(!coOptions.getTimeStampNow());
    com.addCheckoutListener(colistener);

    com.checkout(filesToCheckout.toArray(new File[0]));
    if (com.canCommit()) {
      logger.println("*** " + sdf.format(new Date()) + " checked out request commit");
      com.commit();
      logger.println("*** " + sdf.format(new Date()) + " checked out request committed");
    } else {
      logger.println("*** " + sdf.format(new Date()) + " checked out not commit");
    }

    logger.println("*** " + sdf.format(new Date()) + " removing [" + changeSet.getFilesToRemove().size() + "] files");
    boolean quietDelete = changeSet.getFilesToRemove().size() > 100;
    if (quietDelete) {
      logger.println("*** " + sdf.format(new Date()) + " More than 100 files, quiet mode enabled");
    }
    for (java.io.File f : changeSet.getFilesToRemove()) {
      if (f.exists()) {
        if (!quietDelete)
          logger.println("*** " + sdf.format(new Date()) + " [remove] [" + f + "]");
        f.delete();
      } else {
        logger.println("*** " + sdf.format(new Date()) + " [remove:warn] Planned to remove [" + f + "]");
      }
    }
    logger.println("*** " + sdf.format(new Date()) + " storing change set");
    OutputStream os = null;
    try {
      os = new BufferedOutputStream(filePointFilePath.write());
      StarTeamFilePointFunctions.storeCollection(os, changeSet.getFilePointsToRemember());
    } catch (InterruptedException e) {
      logger.println("*** " + sdf.format(new Date()) + " unable to store change set " + e.getMessage());
    } finally {
      if (os != null) {
        os.close();
      }
    }
    logger.println("*** " + sdf.format(new Date()) + " checkout done. used " + (System.currentTimeMillis() - startTime) + "ms.");
  }

  private static class CheckoutListenerImpl implements CheckoutListener {

    public CheckoutListenerImpl(PrintStream logger) {
      this.logger = logger;
    }

    private SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm:ss");
    NumberFormat nf = NumberFormat.getPercentInstance();
    long lastUpdate = -1;
    int total = 0;
    int finishedCount = -1;
    java.io.File lastFile;
    java.io.File currentFile;
    float percentage = 0.0f;
    int updateInterval = 5000;
    private PrintStream logger;
    boolean isUpdateLastModifyDate = false;

    @Override
    public void notifyProgress(CheckoutEvent event) {
      currentFile = event.getCurrentWorkingFile();
      if (event.getError() != null) {
        logger.println(event.toString());
      }
      if (lastFile == null || !lastFile.equals(event.getCurrentWorkingFile())) {
        lastFile = event.getCurrentWorkingFile();
        finishedCount++;
      }
      if (total > 2000) {
        updateInterval = 10000;
      }
      // if(finishedCount==total){
      // logger.println("*** checkout file finished, start update file last modify date.");
      // }
      percentage = (float) (finishedCount % total) / (float) total;
      if (System.currentTimeMillis() - lastUpdate > updateInterval || finishedCount == total) {
        lastUpdate = System.currentTimeMillis();
        logger.println("*** " + sdf.format(new Date()) + " checked out " + finishedCount + "/" + total + " " + nf.format(percentage) + " last file: "
            + (lastFile == null ? "" : lastFile.getAbsolutePath()));
      }
    }

    @Override
    public void startFile(CheckoutEvent event) {
      total++;
    }

    public java.io.File getCurrentFile() {
      return currentFile;
    }

    public void setUpdateLastModifyDate(boolean isUpdateLastModifyDate) {
      this.isUpdateLastModifyDate = isUpdateLastModifyDate;
    }

  }

  /**
   * Returns the name of the user on the StarTeam server with the specified id.
   * StarTeam stores user IDs as int values and this method will translate those
   * into the actual user name. <br/>
   * This can be used, for example, with a StarTeam {@link Item}'s
   * {@link Item#getModifiedBy()} property, to determine the name of the user
   * who made a modification to the item.
   *
   * @param userId the id of the user on the StarTeam Server
   * @return the name of the user as provided by the StarTeam Server
   */
  public String getUsername(User stUser) {
    String userName = stUser.getName();
    ServerAdministration srvAdmin = server.getAdministration();
    User[] userAccts = null;
    if (canReadUserAccts) {
      try {
        userAccts = srvAdmin.getUsers();
      } catch (Exception e) {
        // System.out.println("WARNING: Looks like this user does not have the permission to access UserAccounts on the StarTeam Server!");
        // System.out.println("WARNING: Please contact your administrator and ask to be given the permission \"Administer User Accounts\" on the server.");
        // System.out.println("WARNING: Defaulting to just using User Full Names which breaks the ability to send email to the individuals who break the build in Hudson!");
        canReadUserAccts = false;
      }
    }
    if (userAccts != null) {
      for (int i = 0; i < userAccts.length; i++) {
        User ua = userAccts[i];
        if (ua.getName().equals(userName)) {
          // System.out.println("INFO: From \'" + userName +
          // "\' found existing user LogonName = " +
          // ua.getLogOnName() + " with ID \'" + ua.getID() + "\' and email \'"
          // + ua.getEmailAddress() +"\'");
          int index = ua.getEmailAddress().indexOf("@");
          if (index > -1) {
            return ua.getEmailAddress().substring(0, ua.getEmailAddress().indexOf("@"));
          }
        }
      }
    } else {
      // Since the user account running the build does not have user admin perms
      // use the User Full Name
      return userName;
    }
    return userName;
  }

  public Folder getRootFolder() {
    return rootFolder;
  }

  public DateTime getServerTime() {
    return server.getCurrentTime();
  }

  /**
   * @param server
   * @param projectname
   * @return Project specified by the projectname
   * @throws StarTeamSCMException
   */
  static Project findProjectOnServer(final Server server, final String projectname) throws StarTeamSCMException {
    for (Project project : server.getProjects()) {
      if (project.getName().equals(projectname)) {
        return project;
      }
    }
    throw new StarTeamSCMException("Couldn't find project " + projectname + " on server " + server.getAddress());
  }

  /**
   * @param project
   * @param viewname
   * @return
   * @throws StarTeamSCMException
   */
  static View findViewInProject(final Project project, final String viewname) throws StarTeamSCMException {
    for (View view : project.getAccessibleViews()) {
      if (view.getName().equals(viewname)) {
        return view;
      }
    }
    throw new StarTeamSCMException("Couldn't find view " + viewname + " in project " + project.getName());
  }

  /**
   * Close the connection.
   */
  public void close() {
    if (server.isConnected()) {
      if (rootFolder != null) {
        rootFolder.discardItems(server.getTypes().FILE, -1);
        rootFolder.discardItems(server.getTypes().FOLDER, -1);
      }
      view.discard();
      project.discard();
      server.disconnect();
    }
  }

  @Override
  protected void finalize() throws Throwable {
    close();
  }

  @Override
  public boolean equals(Object object) {
    if (null == object)
      return false;

    if (!getClass().equals(object.getClass()))
      return false;

    StarTeamConnection other = (StarTeamConnection) object;

    return port == other.port && hostName.equals(other.hostName) && userName.equals(other.userName)
        && password.equals(other.password) && projectName.equals(other.projectName) && viewName.equals(other.viewName)
        && folderName.equals(other.folderName);
  }

  @Override
  public int hashCode() {
    return userName.hashCode();
  }

  @Override
  public String toString() {
    return "host: " + hostName + ", port: " + Integer.toString(port) + ", user: " + userName
        + ", passwd: ******, project: " + projectName + ", view: " + viewName + ", folder: " + folderName;
  }

  /**
   * @param rootFolder         main project directory
   * @param workspace          a workspace directory
   * @param historicFilePoints a collection containing File Points to be compared (previous
   *                           build)
   * @param logger             a logger for consuming log messages
   * @return set of changes
   * @throws StarTeamSCMException
   * @throws IOException
   */
  public StarTeamChangeSet computeChangeSet(Folder rootFolder, java.io.File workspace,
                                            final Collection<StarTeamFilePoint> historicFilePoints, PrintStream logger) throws StarTeamSCMException,
      IOException {
    // --- compute changes as per starteam
    long start = System.currentTimeMillis();
    long st = start;
    final Collection<com.starteam.File> starteamFiles = StarTeamFunctions.listAllFiles(rootFolder, workspace);
    logger.println("*** " + sdf.format(new Date()) + " compute ChangeSet listAllFiles took " + (System.currentTimeMillis() - st) + " ms.");
    st = System.currentTimeMillis();
    final Map<java.io.File, com.starteam.File> starteamFileMap = StarTeamFunctions.convertToFileMap(starteamFiles);

    final Collection<java.io.File> starteamFileSet = starteamFileMap.keySet();
    final Collection<StarTeamFilePoint> starteamFilePoint = StarTeamFilePointFunctions
        .convertFilePointCollection(starteamFiles);
    logger.println("*** " + sdf.format(new Date()) + " compute ChangeSet convertToFileMap took " + (System.currentTimeMillis() - st) + " ms.");
    st = System.currentTimeMillis();
    final Collection<java.io.File> fileSystemFiles = StarTeamFilePointFunctions.listAllFiles(workspace);
    final Collection<java.io.File> fileSystemRemove = new TreeSet<java.io.File>(fileSystemFiles);
    fileSystemRemove.removeAll(starteamFileSet);

    final StarTeamChangeSet changeSet = new StarTeamChangeSet();

    changeSet.setFilesToRemove(fileSystemRemove);
    changeSet.setFilePointsToRemember(starteamFilePoint);
    // changeSet.setFilesToCheckout(starteamFiles);
    // --- compute differences as per historic storage file
    logger.println("*** " + sdf.format(new Date()) + " compute ChangeSet changeSet took " + (System.currentTimeMillis() - st) + " ms.");
    st = System.currentTimeMillis();
    if (historicFilePoints != null && !historicFilePoints.isEmpty()) {

      try {

        changeSet.setComparisonAvailable(true);
        logger.println("*** " + sdf.format(new Date()) + " compute Difference from historic file points.");
        computeDifference(starteamFilePoint, historicFilePoints, changeSet, starteamFileMap, fileSystemFiles, logger);

      } catch (Throwable t) {
        t.printStackTrace(logger);
      }
    } else {
      // add all star team files
      logger.println("*** " + sdf.format(new Date()) + " compute Difference add all star team files.");
      Collection<File> result = new ArrayList<File>();
      MD5 localFileMD5 = new MD5();
      for (File file : starteamFiles) {
        MD5 starteamFileMD5 = file.getMD5();

        java.io.File localFile = new java.io.File(file.getFullName());

        if (localFile.exists()) {
          localFileMD5.computeFileMD5(localFile);
          if (file.getContentModifiedTime().toJavaMsec() == localFile.lastModified() || starteamFileMD5.equals(localFileMD5)) {
            continue;
          } else {
            logger.println(" File " + file.getFullName() + " MD5:" + localFileMD5 + " starteam MD5:" + starteamFileMD5);
          }
        }
        result.add(file);
        changeSet.addChange(FileToStarTeamChangeLogEntry(file));
      }
      changeSet.setFilesToCheckout(result);
    }
    logger.println("*** " + sdf.format(new Date()) + " compute ChangeSet computeDifference took " + (System.currentTimeMillis() - st) + " ms.");
    st = System.currentTimeMillis();
    logger.println("*** " + sdf.format(new Date()) + " compute ChangeSet took " + (System.currentTimeMillis() - start) + " ms.");
    return changeSet;
  }

  public StarTeamChangeLogEntry FileToStarTeamChangeLogEntry(File f) {
    return FileToStarTeamChangeLogEntry(f, "change");
  }

  public StarTeamChangeLogEntry FileToStarTeamChangeLogEntry(File f, String change) {
    int revisionNumber = VersionedObject.getViewVersion(f.getDotNotation());
    String username = getUsername(f.getModifiedBy());
    String msg = f.getComment();
    Date date = new Date(f.getModifiedTime().toJavaMsec());
    String fileName = f.getName();

    return new StarTeamChangeLogEntry(fileName, revisionNumber, date, username, msg, change);
  }

  public StarTeamChangeSet computeDifference(final Collection<StarTeamFilePoint> currentFilePoint,
                                             final Collection<StarTeamFilePoint> historicFilePoint, StarTeamChangeSet changeSet,
                                             Map<java.io.File, com.starteam.File> starteamFileMap, Collection<java.io.File> filesOnDisk, PrintStream logger) {
    logger.println("*** " + sdf.format(new Date()) + " computeDifference start.");
    final Map<java.io.File, StarTeamFilePoint> starteamFilePointMap = StarTeamFilePointFunctions
        .convertToFilePointMap(currentFilePoint);
    Map<java.io.File, StarTeamFilePoint> historicFilePointMap = StarTeamFilePointFunctions
        .convertToFilePointMap(historicFilePoint);

    final Set<java.io.File> starteamOnly = new HashSet<java.io.File>();
    starteamOnly.addAll(starteamFilePointMap.keySet());
    starteamOnly.removeAll(historicFilePointMap.keySet());

    final Set<java.io.File> historicOnly = new HashSet<java.io.File>();
    historicOnly.addAll(historicFilePointMap.keySet());
    historicOnly.removeAll(starteamFilePointMap.keySet());

    final Set<java.io.File> common = new HashSet<java.io.File>();
    common.addAll(starteamFilePointMap.keySet());
    common.removeAll(starteamOnly);

    StarTeamChangeLogEntry change;
    Collection<File> fileToCheckout = new ArrayList<File>();
    //int i = 0;
    for (java.io.File f : common) {
      StarTeamFilePoint starteam = starteamFilePointMap.get(f);
      StarTeamFilePoint historic = historicFilePointMap.get(f);
      if (starteam.getRevisionNumber() == historic.getRevisionNumber()
          && starteam.getLastModifyDate() == historic.getFile().lastModified()) {
        // unchanged files
        continue;
      }
      com.starteam.File stf = starteamFileMap.get(f);
      if (starteam.getRevisionNumber() > historic.getRevisionNumber()) {
        // higher.add(f);
        changeSet.addChange(FileToStarTeamChangeLogEntry(stf, "change"));
      } else if (starteam.getRevisionNumber() < historic.getRevisionNumber()) {
        // lower.add(f);
        changeSet.addChange(FileToStarTeamChangeLogEntry(stf, "rollback"));
      } else {
        changeSet.addChange(FileToStarTeamChangeLogEntry(stf, "change"));
      }
      fileToCheckout.add(stf);
    }

    for (java.io.File f : historicOnly) {
      StarTeamFilePoint historic = historicFilePointMap.get(f);
      change = new StarTeamChangeLogEntry(f.getName(), historic.getRevisionNumber(), new Date(), "Unknow", "file deleted", "removed");
      changeSet.addChange(change);
    }
    for (java.io.File f : starteamOnly) {
      com.starteam.File stf = starteamFileMap.get(f);
      changeSet.addChange(FileToStarTeamChangeLogEntry(stf, "added"));
      fileToCheckout.add(stf);
    }
    changeSet.setFilesToCheckout(fileToCheckout);
    logger.println("*** " + sdf.format(new Date()) + " computeDifference end.");
    return changeSet;
  }
}
