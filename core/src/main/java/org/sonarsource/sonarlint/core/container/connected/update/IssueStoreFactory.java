package org.sonarsource.sonarlint.core.container.connected.update;

import java.nio.file.Path;

public interface IssueStoreFactory {
  /**
   * Create a filesystem-backed issue store at specified base directory.
   *
   * @param basedir the root directory of the issue store
   * @return a new issue store
   */
  IssueStore create(Path basedir);
}
