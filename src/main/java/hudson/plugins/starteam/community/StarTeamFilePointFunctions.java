package hudson.plugins.starteam.community;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * Functions operating on StarTeamFilePoint type.
 */

public class StarTeamFilePointFunctions {

  private StarTeamFilePointFunctions() {
    throw new InstantiationError();
  }
  // projection and collection conversion

  /**
   * @param collection Collection of StarTeam files
   * @return collection of full path file names
   */
  public static Collection<java.io.File> convertToFileCollection(final Collection<com.starteam.File> collection) {
    Collection<java.io.File> result = new ArrayList<java.io.File>();
    for (com.starteam.File f : collection) {
      result.add(new java.io.File(f.getFullName()));
    }

    return result;
  }

  /**
   * @param collection Collection of StarTeam files
   * @return collection of FilePoints - information vector needed keeping track of file status
   */
  public static Collection<StarTeamFilePoint> convertFilePointCollection(final Collection<com.starteam.File> collection) {
    Collection<StarTeamFilePoint> result = new ArrayList<StarTeamFilePoint>();
    for (com.starteam.File f : collection) {
      result.add(new StarTeamFilePoint(f));
    }
    return result;
  }

  public static Collection<StarTeamFilePoint> extractFilePointSubCollection(final Map<java.io.File, StarTeamFilePoint> map,
                                                                            final Collection<java.io.File> collection) {
    Collection<StarTeamFilePoint> result = new ArrayList<StarTeamFilePoint>();
    for (java.io.File f : collection) {
      result.add(map.get(f));
    }
    return result;
  }

  public static Collection<com.starteam.File> extractFileSubCollection(final Map<java.io.File, com.starteam.File> map, final Collection<java.io.File> collection) {
    Collection<com.starteam.File> result = new ArrayList<com.starteam.File>();
    for (java.io.File f : collection) {
      result.add(map.get(f));
    }
    return result;
  }

  public static Map<java.io.File, StarTeamFilePoint> convertToFilePointMap(final Collection<StarTeamFilePoint> collection) {
    Map<java.io.File, StarTeamFilePoint> result = new HashMap<java.io.File, StarTeamFilePoint>();
    for (StarTeamFilePoint fp : collection) {
      result.put(fp.getFile(), fp);
    }
    return result;
  }

  /**
   * Recursive file system discovery
   *
   * @param workspace a Hudson workspace directory
   * @return collection of files within workspace
   */
  public static Collection<java.io.File> listAllFiles(final java.io.File workspace) {
    Collection<java.io.File> result = new ArrayList<java.io.File>();
    listAllFiles(result, workspace.getAbsoluteFile());
    return result;
  }

  private static void listAllFiles(final Collection<java.io.File> result, final java.io.File dir) {
    List<java.io.File> sub = new ArrayList<java.io.File>();
    java.io.File[] files = dir.listFiles();
    if (files != null) {
      for (java.io.File f : files) {
        if (f.isFile()) {
          result.add(f);
        } else if (f.isDirectory()) {
          sub.add(f);
        }
      }
      for (java.io.File f : sub) {
        listAllFiles(result, f);
      }
    } else {
      if (dir.isFile()) {
        result.add(dir);
      }
    }
  }

  // storage

  @SuppressWarnings("unchecked")
  public static Collection<StarTeamFilePoint> loadCollection(final java.io.File file) throws IOException {
    Collection<String> stringCollection = FileUtils.readLines(file, "ISO-8859-1");
    Collection<StarTeamFilePoint> result = new ArrayList<StarTeamFilePoint>();
    for (String str : stringCollection) {

      String[] data = str.split(",");

      String revision = data[0];
      String lastModifyTime = data[1];
      String path;
      if (data.length == 3) {
        path = data[2];
      } else {
        path = data[1];
        lastModifyTime = "0";
      }
      StarTeamFilePoint f = new StarTeamFilePoint(path, Integer.parseInt(revision), Long.parseLong(lastModifyTime));

      result.add(f);
    }

    return result;
  }

  public static void storeCollection(final OutputStream bos, final Collection<StarTeamFilePoint> collection) throws IOException {
    Collection<String> stringCollection = new ArrayList<String>();
    for (StarTeamFilePoint i : collection) {
      stringCollection.add(i.getRevisionNumber() + "," + i.getLastModifyDate() + "," + i.getFullfilepath());
    }
    IOUtils.writeLines(stringCollection, null, bos, "ISO-8859-1");
  }

}
