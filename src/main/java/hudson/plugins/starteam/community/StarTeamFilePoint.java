package hudson.plugins.starteam.community;

import com.starteam.VersionedObject;

import java.io.File;
import java.io.Serializable;
import java.util.Date;

/**
 * Stores a reference to the file at the particular revision.
 */
public class StarTeamFilePoint implements Serializable, Comparable {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  private String fullfilepath;
  private int revisionnumber;
  private long lastModifyDate;

  public StarTeamFilePoint() {
    super();
  }

  public StarTeamFilePoint(com.starteam.File f) {

    this(f.getFullName(), VersionedObject.getViewVersion(f.getDotNotation()), f.getContentModifiedTime().toJavaMsec());
  }

  public StarTeamFilePoint(String fullFilePath, int revisionNumber, long lastModifyDate) {
    this.fullfilepath = fullFilePath;
    this.revisionnumber = revisionNumber;
    this.lastModifyDate = lastModifyDate;
  }

  public String getFullfilepath() {
    return fullfilepath;
  }

  public long getLastModifyDate() {
    return this.lastModifyDate;
  }

  public File getFile() {
    return new File(getFullfilepath());
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    StarTeamFilePoint that = (StarTeamFilePoint) o;

    if (fullfilepath != null ? !fullfilepath.equals(that.fullfilepath) : that.fullfilepath != null) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    int result;
    result = (fullfilepath != null ? fullfilepath.hashCode() : 0);
    return result;
  }

  public int compareTo(Object o) {
    return fullfilepath.toLowerCase().compareTo(((StarTeamFilePoint) o).fullfilepath.toLowerCase());
  }

  public int getRevisionNumber() {
    return revisionnumber;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("file: ").append(fullfilepath);
    builder.append(" revision: ").append(revisionnumber);
    builder.append("lastModifyDate:").append(new Date(lastModifyDate));
    return builder.toString();
  }

}
