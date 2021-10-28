package org.sonarsource.sonarlint.core.analysis.container.analysis;

import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.analysis.api.Issue;

/**
 * We need a dedicated class for dependency injection
 *
 */
public class IssueListener {
  private final Consumer<Issue> wrapped;

  public IssueListener(Consumer<Issue> issueListener) {
    this.wrapped = issueListener;
  }

  public void handle(Issue issue) {
    wrapped.accept(issue);
  }
}
