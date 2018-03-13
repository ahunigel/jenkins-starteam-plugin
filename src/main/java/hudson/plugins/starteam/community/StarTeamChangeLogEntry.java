package hudson.plugins.starteam.community;

import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.tasks.Mailer;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;

/**
 * <p>
 * Implementation of {@link hudson.scm.ChangeLogSet.Entry} for StarTeam SCM.
 * </p>
 *
 * @author Eric D. Broyles
 * @version 1.0
 */
public class StarTeamChangeLogEntry extends hudson.scm.ChangeLogSet.Entry {

  private int revisionNumber;

  private String username;

  private String msg;

  private Date date;

  private String fileName;

  private String changeType;

  public StarTeamChangeLogEntry(String fileName, int revisionNumber, Date date,
                                String username, String msg, String changeType) {
    super();
    this.revisionNumber = revisionNumber;
    this.username = username;
    this.msg = msg;
    this.date = date;
    this.fileName = fileName;
    this.changeType = changeType;
  }

  public StarTeamChangeLogEntry() {
    super();
  }

  @Override
  public Collection<String> getAffectedPaths() {
    Collection<String> list = new LinkedList<String>();
    list.add(fileName);
    return list;
  }

  /**
   * Gets the Hudson user based upon the StarTeam {@link #username}.
   *
   * @see hudson.scm.ChangeLogSet.Entry#getAuthor()
   */
  @Override
  public User getAuthor() {
    User user = User.get(username);
    user.setFullName("");
    new Mailer.UserProperty("");

    return user;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String aUsername) {
    this.username = aUsername;
  }

  @Override
  public String getMsg() {
    if (msg == null) {
      return "";
    }
    return msg;
  }

  public void setMsg(String aMsg) {
    this.msg = aMsg;
  }

  public int getRevisionNumber() {
    return revisionNumber;
  }

  public void setRevisionNumber(int aRevisionNumber) {
    this.revisionNumber = aRevisionNumber;
  }

  public Date getDate() {
    return date;
  }

  public void setDate(Date aDate) {
    this.date = aDate;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String aFileName) {
    this.fileName = aFileName;
  }

  public String getChangeType() {
    return changeType;
  }

  public void setChangeType(String aChange) {
    this.changeType = aChange;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("file: ").append(fileName);
    builder.append(" revision: ").append(revisionNumber);
    builder.append(" date: ").append(date);
    builder.append(" changeType: ").append(changeType);
    builder.append(" user: ").append(username);
    builder.append(" mgs: ").append(msg);
    return builder.toString();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setParent(ChangeLogSet parent) {
    super.setParent(parent);
  }
}
