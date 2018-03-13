/**
 *
 */
package hudson.plugins.starteam.community;

import hudson.model.Descriptor;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.RepositoryBrowser;

import java.io.IOException;
import java.net.URL;

/**
 * @author Ilkka Laukkanen <ilkka.s.laukkanen@gmail.com>
 * TODO implement maybe?
 */
public abstract class StarTeamRepositoryBrowser extends RepositoryBrowser {

  /*
   * (non-Javadoc)
   *
   * @see hudson.scm.RepositoryBrowser#getChangeSetLink(hudson.scm.ChangeLogSet.Entry)
   */
  @Override
  public URL getChangeSetLink(Entry changeSet) throws IOException {
    return null;
  }

  /*
   * (non-Javadoc)
   *
   * @see hudson.model.Describable#getDescriptor()
   */
  public Descriptor getDescriptor() {
    return null;
  }

}
