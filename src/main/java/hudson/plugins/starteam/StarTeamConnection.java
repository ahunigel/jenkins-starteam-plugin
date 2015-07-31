/**
 *
 */
package hudson.plugins.starteam;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.starbase.starteam.CheckoutEvent;
import com.starbase.starteam.CheckoutListener;
import com.starbase.starteam.CheckoutManager;
import com.starbase.starteam.CheckoutOptions;
import com.starbase.starteam.CheckoutProgress;
import com.starbase.starteam.File;
import com.starbase.starteam.Folder;
import com.starbase.starteam.Item;
import com.starbase.starteam.LogonException;
import com.starbase.starteam.Project;
import com.starbase.starteam.PropertyNames;
import com.starbase.starteam.Server;
import com.starbase.starteam.ServerAdministration;
import com.starbase.starteam.ServerConfiguration;
import com.starbase.starteam.ServerInfo;
import com.starbase.starteam.Status;
import com.starbase.starteam.UserAccount;
import com.starbase.starteam.View;
import com.starbase.util.OLEDate;

/**
 * StarTeamActor is a class that implements connecting to a StarTeam repository,
 * to a given project, view and folder.
 * 
 * Add functionality allowing to delete non starteam file in folder while
 * performing listing of all files. and to perform creation of changelog file
 * during the checkout
 * 
 * @author Ilkka Laukkanen <ilkka.s.laukkanen@gmail.com>
 * @author Steve Favez <sfavez@verisign.com>
 * 
 */
public class StarTeamConnection implements Serializable {
	private static final long serialVersionUID = 1L;

	public static final String FILE_POINT_FILENAME = "starteam-filepoints.csv";

	private final String hostName;
	private final int port;
	private final String userName;
	private final String password;
	private final String projectName;
	private final String viewName;
	private final String folderName;
	private final StarTeamViewSelector configSelector;

	private transient Server server;
	private transient View view;
	private transient Folder rootFolder;
	private transient Project project;
	private transient ServerAdministration srvAdmin;

	private boolean canReadUserAccts = true;

	private UserAccount[] userAccts = null;

	private Map<Integer, String> userNameCache = new HashMap<Integer, String>();

	private PrintStream logger;

	/**
	 * Default constructor
	 * 
	 * @param hostName
	 *            the starteam server host / ip name
	 * @param port
	 *            starteam server port
	 * @param userName
	 *            user used to connect starteam server
	 * @param password
	 *            user password to connect to starteam server
	 * @param projectName
	 *            starteam project's name
	 * @param viewName
	 *            starteam view's name
	 * @param folderName
	 *            starteam folder's name
	 * @param configSelector
	 *            configuration selector in case of checking from label,
	 *            promotion state or time
	 */
	public StarTeamConnection(String hostName, int port, String userName,
			String password, String projectName, String viewName,
			String folderName, StarTeamViewSelector configSelector) {
		checkParameters(hostName, port, userName, password, projectName,
				viewName, folderName);
		this.hostName = hostName;
		this.port = port;
		this.userName = userName;
		this.password = password;
		this.projectName = projectName;
		this.viewName = viewName;
		this.folderName = folderName;
		this.configSelector = configSelector;
	}

	public StarTeamConnection(StarTeamConnection oldConnection,
			StarTeamViewSelector configSelector) {
		this(oldConnection.hostName, oldConnection.port,
				oldConnection.userName, oldConnection.password,
				oldConnection.projectName, oldConnection.viewName,
				oldConnection.folderName, configSelector);
	}

	private ServerInfo createServerInfo() {
		ServerInfo serverInfo = new ServerInfo();
		serverInfo
				.setConnectionType(ServerConfiguration.PROTOCOL_TCP_IP_SOCKETS);
		serverInfo.setHost(this.hostName);
		serverInfo.setPort(this.port);

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
			serverInfo.setDescription("StarTeam connection to "
					+ this.hostName
					+ ((counter == 0) ? "" : " (" + Integer.toString(counter)
							+ ")"));
			return true;
		} catch (com.starbase.starteam.DuplicateServerListEntryException e) {
			return false;
		}
	}

	private void checkParameters(String hostName, int port, String userName,
			String password, String projectName, String viewName,
			String folderName) {
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
	 * Initialize the connection. This means logging on to the server and
	 * finding the project, view and folder we want.
	 * 
	 * @throws StarTeamSCMException
	 *             if logging on fails.
	 */
	public void initialize() throws StarTeamSCMException {
		Long startTime = System.currentTimeMillis();
		System.out.println("Initialzing...");
		server = new Server(createServerInfo());
		server.connect();
		try {
			server.logOn(userName, password);
		} catch (LogonException e) {
			throw new StarTeamSCMException("Could not log on: "
					+ e.getErrorMessage());
		}

		project = findProjectOnServer(server, projectName);
		view = findViewInProject(project, viewName);
		if (configSelector != null) {
			View configuredView = null;
			try {
				configuredView = configSelector.configView(view);
			} catch (ParseException e) {
				throw new StarTeamSCMException(
						"Could not correctly parse configuration date: "
								+ e.getMessage());
			}
			if (configuredView != null)
				view = configuredView;
		}
		rootFolder = StarTeamFunctions.findFolderInView(view, folderName);

		// Cache some folder data
		final PropertyNames pnames = rootFolder.getPropertyNames();
		final String[] propsToCache = new String[] {
				pnames.FILE_LOCAL_FILE_EXISTS, pnames.FILE_LOCAL_TIMESTAMP,
				pnames.FILE_NAME, pnames.FILE_FILE_TIME_AT_CHECKIN,
				pnames.MODIFIED_TIME, pnames.MODIFIED_USER_ID,
				pnames.FILE_STATUS
		// ,pnames.FILE_CONTENT_REVISION
		// ,pnames.COMMENT
		};
		rootFolder.populateNow(server.getTypeNames().FILE, propsToCache, -1);
		rootFolder.populateNow(server.getTypeNames().FOLDER,
				new String[] { pnames.FOLDER_WORKING_FOLDER }, -1);
		System.out.println("Initialze complete, take "
				+ (System.currentTimeMillis() - startTime) + " ms.");
	}

	/**
	 * checkout the files from starteam
	 * 
	 * @param changeSet
	 *            a description of changes
	 * @param buildFolder
	 *            A root folder for given build. it is used for storing
	 *            information.
	 * @throws IOException
	 *             if checkout fails.
	 */
	public void checkOut(StarTeamChangeSet changeSet,
			final java.io.File workspace, final PrintStream logger,
			final java.io.File buildFolder) throws IOException {
		this.logger = logger;
		long start = System.currentTimeMillis();
		logger.println("*** Performing checkout on ["
				+ changeSet.getChanges().size() + "] files");
		boolean quietCheckout = changeSet.getChanges().size() >= 2000;
		if (quietCheckout) {
			logger.println("*** More than 2000 files, quiet mode enabled");
		}
		List<File> filesToCheckout = new ArrayList<File>();
		for (File f : changeSet.getFilesToCheckout()) {

			boolean dirty = true;
			switch (f.getStatus()) {
			case Status.UNKNOWN:
				dirty = false;
			case Status.NEW:
			case Status.MERGE:
			case Status.MODIFIED:
				// clobber these
				new java.io.File(f.getFullName()).delete();
				if (!quietCheckout)
					logger.println("[co] Deleted File: " + f.getFullName());
				break;
			case Status.MISSING:
			case Status.OUTOFDATE:
				dirty = false;
				// just go on and check out
				break;
			default:
				// By default do nothing, go to next iteration
				continue;
			}
			filesToCheckout.add(f);
			// if (!quietCheckout)
			// logger.println("[co] " + f.getFullName() + "... attempt");
			// try {
			// f.checkout(Item.LockType.UNLOCKED, // check out as unlocked
			// true, // use timestamp from local time
			// true, // convert EOL to native format
			// true); // update status
			// } catch (IOException e) {
			// logger
			// .print("[checkout] [exception] [Problem checking out file: "
			// + f.getFullName()
			// + "] \n"
			// + ExceptionUtils.getFullStackTrace(e) + "\n");
			// throw e;
			// } catch (RuntimeException e) {
			// logger
			// .print("[checkout] [exception] [Problem checking out file: "
			// + f.getFullName()
			// + "] \n"
			// + ExceptionUtils.getFullStackTrace(e) + "\n");
			// throw e;
			// }
			if (dirty) {
				changeSet.getChanges().add(
						FileToStarTeamChangeLogEntry(f, "dirty"));
			}
			// if (!quietCheckout) logger.println("[co] " + f.getFullName() +
			// "... ok");
			// f.discard();
		}
		CheckoutOptions coOptions = new CheckoutOptions(view);
		coOptions.setLockType(Item.LockType.UNLOCKED);
		coOptions.setEOLConversionEnabled(true);
		coOptions.setUpdateStatus(true);
		coOptions.setTimeStampNow(true);
		CheckoutManager com = view.createCheckoutManager(coOptions);
		final int total = filesToCheckout.size();

		com.addCheckoutListener(new CheckoutListener() {
			NumberFormat nf = NumberFormat.getPercentInstance();
			long lastUpdate = -1;

			@Override
			public void onNotifyProgress(CheckoutEvent event) {
				// if(event.isFinished()){
				// logger.println("***check out finished.result:"+(event.isSuccessful()?"Successful":"Failed"));
				// }
				// else
				// {
				if (System.currentTimeMillis() - lastUpdate > 1000) {
					lastUpdate = System.currentTimeMillis();
					logger.println("***checked out "
							+ event.getProgress().unwrap().toString());
				}
				// }
				// logger.println(event);
			}

			@Override
			public void onStartFile(CheckoutEvent event) {
				// logger.println(System.currentTimeMillis()+" "+event.getCurrentFile());
			}

		});
		if (total > 0) {
			new Thread() {

				public void run() {
					int startCount = getFilesCount(workspace);
					int count = startCount;
					int end = startCount + total;
					float percentage = 0;
					float lastpercent = 0;
					NumberFormat nf = NumberFormat.getPercentInstance();
					int sleeptimes = 0;
					StringBuilder sb;
					logger.println("Check out files [====================================================================================================] 0.0%");
					while (count < end) {
						try {
							Thread.sleep(2000);
							sleeptimes++;
							count = getFilesCount(workspace);
							percentage = ((float) (count - startCount)) / total;

							if (sleeptimes == 10
									|| percentage - lastpercent > 0.05) {
								sleeptimes = 0;
								sb = new StringBuilder();
								sb.append("Check out files [");

								int c = (int) (percentage * 100);
								for (int i = 0; i < c; i++) {
									sb.append(">");
								}
								for (int i = c; i < 100; i++) {
									sb.append("=");
								}
								sb.append("] ").append(nf.format(percentage));
								logger.println(sb.toString());
								lastpercent = percentage;
							}
						} catch (InterruptedException e) {
							break;
						}
					}
					logger.println("Check out files [>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>] 100%");
				}
			}.start();
		}
		com.checkout(filesToCheckout.toArray(new File[0]));

		//
		logger.println("*** removing [" + changeSet.getFilesToRemove().size()
				+ "] files");
		boolean quietDelete = changeSet.getFilesToRemove().size() > 100;
		if (quietDelete) {
			logger.println("*** More than 100 files, quiet mode enabled");
		}
		for (java.io.File f : changeSet.getFilesToRemove()) {
			if (f.exists()) {
				if (!quietDelete)
					logger.println("[remove] [" + f + "]");
				f.delete();
			} else {
				logger.println("[remove:warn] Planned to remove [" + f + "]");
			}
		}

		// buildFolder is null if we're building on a remote slave
		// Currently, the plugin checks out code on the master and on the remote
		// slave.
		// Consequently, we only need to store the filePointFile on the master.
		// Slaves
		// don't have a good place to store metadata like this, anyway.
		if (buildFolder != null) {
			java.io.File filePointFile = new java.io.File(buildFolder,
					FILE_POINT_FILENAME);
			logger.println("*** storing change set");
			StarTeamFilePointFunctions.storeCollection(filePointFile,
					changeSet.getFilePointsToRemember());
		}
		logger.println("*** checkout done, took "
				+ (System.currentTimeMillis() - start) + " ms.");
	}

	private int getFilesCount(java.io.File file) {
		java.io.File[] files = file.listFiles();
		int count = 0;
		for (java.io.File f : files) {
			if (f.isFile()) {
				count++;
			} else {
				count += getFilesCount(f);
			}
		}
		return count;

	}

	/**
	 * Returns the name of the user on the StarTeam server with the specified
	 * id. StarTeam stores user IDs as int values and this method will translate
	 * those into the actual user name. <br/>
	 * This can be used, for example, with a StarTeam {@link Item}'s
	 * {@link Item#getModifiedBy()} property, to determine the name of the user
	 * who made a modification to the item.
	 * 
	 * @param userId
	 *            the id of the user on the StarTeam Server
	 * @return the name of the user as provided by the StarTeam Server
	 */
	public String getUsername(int userId) {
		if (userAccts == null && canReadUserAccts) {
			try {
				srvAdmin = server.getAdministration();
				userAccts = srvAdmin.getUserAccounts();
			} catch (Exception e) {
				if (logger != null) {
					logger.println("WARNING: Looks like this user does not have the permission to access UserAccounts on the StarTeam Server!");
					logger.println("WARNING: Please contact your administrator and ask to be given the permission \"Administer User Accounts\" on the server.");
					logger.println("WARNING: Defaulting to just using User Full Names which breaks the ability to send email to the individuals who break the build in Hudson!");
				}
				canReadUserAccts = false;
			}
		}
		if (canReadUserAccts) {
			UserAccount ua = userAccts[0];
			for (int i = 0; i < userAccts.length; i++) {
				ua = userAccts[i];
				if (ua.getID() == userId) {
					System.out.println("INFO: From \'" + userId
							+ "\' found existing user LogonName = "
							+ ua.getLogOnName() + " with ID \'" + ua.getID()
							+ "\' and email \'" + ua.getEmailAddress() + "\'");
					int length = ua.getEmailAddress().indexOf('@');
					if (length > -1) {
						return ua.getEmailAddress().substring(0, length);
					} else {
						logger.println("user " + ua.getLogOnName() + " email["
								+ ua.getEmailAddress() + "] is not correct.");
						return ua.getLogOnName();
					}
				}
			}
		} else {
			// Since the user account running the build does not have user admin
			// perms
			// Build the base email name from the User Full Name
			String shortname = getUserNameFromCache(userId);
			if (shortname.indexOf(",") > 0) {
				// check for a space and assume "lastname, firstname"
				shortname = shortname.charAt((shortname.indexOf(" ") + 1))
						+ shortname.substring(0, shortname.indexOf(","));
			} else {
				// check for a space and assume "firstname lastname"
				if (shortname.indexOf(" ") > 0) {
					shortname = shortname.charAt(0)
							+ shortname.substring((shortname.indexOf(" ") + 1),
									shortname.length());

				} // otherwise, do nothing, just return the name we have.
			}
			return shortname;
		}
		return "unknown";
	}

	private String getUserNameFromCache(int userId) {
		String userName = userNameCache.get(userId);
		if (userName == null) {
			userName = server.getUser(userId).getName();
			userNameCache.put(userId, userName);
		}
		return userName;
	}

	public Folder getRootFolder() {
		return rootFolder;
	}

	public OLEDate getServerTime() {
		return server.getCurrentTime();
	}

	/**
	 * @param server
	 * @param projectname
	 * @return Project specified by the projectname
	 * @throws StarTeamSCMException
	 */
	static Project findProjectOnServer(final Server server,
			final String projectname) throws StarTeamSCMException {
		for (Project project : server.getProjects()) {
			if (project.getName().equals(projectname)) {
				return project;
			}
		}
		throw new StarTeamSCMException("Couldn't find project " + projectname
				+ " on server " + server.getAddress());
	}

	/**
	 * @param project
	 * @param viewname
	 * @return
	 * @throws StarTeamSCMException
	 */
	static View findViewInProject(final Project project, final String viewname)
			throws StarTeamSCMException {
		for (View view : project.getAccessibleViews()) {
			if (view.getName().equals(viewname)) {
				return view;
			}
		}
		throw new StarTeamSCMException("Couldn't find view " + viewname
				+ " in project " + project.getName());
	}

	/**
	 * Close the connection.
	 */
	public void close() {
		if (server.isConnected()) {
			if (rootFolder != null) {
				rootFolder.discardItems(rootFolder.getTypeNames().FILE, -1);
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

		return port == other.port && hostName.equals(other.hostName)
				&& userName.equals(other.userName)
				&& password.equals(other.password)
				&& projectName.equals(other.projectName)
				&& viewName.equals(other.viewName)
				&& folderName.equals(other.folderName);
	}

	@Override
	public int hashCode() {
		return userName.hashCode();
	}

	@Override
	public String toString() {
		return "host: " + hostName + ", port: " + Integer.toString(port)
				+ ", user: " + userName + ", passwd: ******, project: "
				+ projectName + ", view: " + viewName + ", folder: "
				+ folderName;
	}

	/**
	 * @param rootFolder
	 *            main project directory
	 * @param workspace
	 *            a workspace directory
	 * @param historicFilePoints
	 *            a collection containing File Points to be compared (previous
	 *            build)
	 * @param logger
	 *            a logger for consuming log messages
	 * @return set of changes
	 * @throws StarTeamSCMException
	 * @throws IOException
	 */
	public StarTeamChangeSet computeChangeSet(Folder rootFolder,
			java.io.File workspace,
			final Collection<StarTeamFilePoint> historicFilePoints,
			PrintStream logger) throws StarTeamSCMException, IOException {
		// --- compute changes as per starteam
		this.logger = logger;
		Long startTime = System.currentTimeMillis();
		logger.println("Starting compute change set.");

		final Collection<com.starbase.starteam.File> starteamFiles = StarTeamFunctions
				.listAllFiles(rootFolder, workspace);
		final Map<java.io.File, com.starbase.starteam.File> starteamFileMap = StarTeamFunctions
				.convertToFileMap(starteamFiles);
		final Collection<java.io.File> starteamFileSet = starteamFileMap
				.keySet();
		final Collection<StarTeamFilePoint> starteamFilePoint = StarTeamFilePointFunctions
				.convertFilePointCollection(starteamFiles);

		final Collection<java.io.File> fileSystemFiles = StarTeamFilePointFunctions
				.listAllFiles(workspace);
		final Collection<java.io.File> fileSystemRemove = new TreeSet<java.io.File>(
				fileSystemFiles);
		fileSystemRemove.removeAll(starteamFileSet);

		final StarTeamChangeSet changeSet = new StarTeamChangeSet();
		changeSet.setFilesToCheckout(starteamFiles);
		changeSet.setFilesToRemove(fileSystemRemove);
		changeSet.setFilePointsToRemember(starteamFilePoint);

		// --- compute differences as per historic storage file

		if (historicFilePoints != null && !historicFilePoints.isEmpty()) {

			try {

				changeSet.setComparisonAvailable(true);

				computeDifference(starteamFilePoint, historicFilePoints,
						changeSet, starteamFileMap);

			} catch (Throwable t) {
				t.printStackTrace(logger);
			}
		} else {
			for (File file : starteamFiles) {
				changeSet.addChange(FileToStarTeamChangeLogEntry(file));
			}
		}
		changeSet.updateChangesCommentsAndRevisionNumber();
		logger.println("End compute change set. took "
				+ (System.currentTimeMillis() - startTime) + "ms.");
		return changeSet;
	}

	public StarTeamChangeLogEntry FileToStarTeamChangeLogEntry(File f) {
		return FileToStarTeamChangeLogEntry(f, "change");
	}

	public StarTeamChangeLogEntry FileToStarTeamChangeLogEntry(File f,
			String change) {
		int revisionNumber = -1;// f.getContentVersion();
		int userId = f.getModifiedBy();
		String username = getUsername(userId);
		String msg = "";// f.getComment();
		Date date = new Date(f.getModifiedTime().getLongValue());
		String fileName = f.getName();

		return new StarTeamChangeLogEntry(f, fileName, revisionNumber, date,
				username, msg, change);
	}

	public StarTeamChangeSet computeDifference(
			final Collection<StarTeamFilePoint> currentFilePoint,
			final Collection<StarTeamFilePoint> historicFilePoint,
			StarTeamChangeSet changeSet,
			Map<java.io.File, com.starbase.starteam.File> starteamFileMap) {

		Long startTime = System.currentTimeMillis();
		System.out.println("Starting compute difference.");

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

		final Set<java.io.File> higher = new HashSet<java.io.File>(); // newer
																		// revision
		final Set<java.io.File> lower = new HashSet<java.io.File>(); // typically
																		// rollback
																		// of a
																		// revision
		StarTeamChangeLogEntry change;

		for (java.io.File f : common) {
			StarTeamFilePoint starteam = starteamFilePointMap.get(f);
			StarTeamFilePoint historic = historicFilePointMap.get(f);

			if (starteam.getRevisionNumber() == historic.getRevisionNumber()) {
				// unchanged files
				continue;
			}
			com.starbase.starteam.File stf = starteamFileMap.get(f);
			if (starteam.getRevisionNumber() > historic.getRevisionNumber()) {
				higher.add(f);
				changeSet
						.addChange(FileToStarTeamChangeLogEntry(stf, "change"));
			}
			if (starteam.getRevisionNumber() < historic.getRevisionNumber()) {
				lower.add(f);
				changeSet.addChange(FileToStarTeamChangeLogEntry(stf,
						"rollback"));
			}
		}

		for (java.io.File f : historicOnly) {
			StarTeamFilePoint historic = historicFilePointMap.get(f);
			change = new StarTeamChangeLogEntry(null, f.getName(),
					historic.getRevisionNumber(), new Date(), "", "", "removed");
			changeSet.addChange(change);
		}
		for (java.io.File f : starteamOnly) {
			com.starbase.starteam.File stf = starteamFileMap.get(f);
			changeSet.addChange(FileToStarTeamChangeLogEntry(stf, "added"));
		}
		System.out.println("End compute difference. tooks "
				+ (System.currentTimeMillis() - startTime) + " ms");
		return changeSet;
	}
}
