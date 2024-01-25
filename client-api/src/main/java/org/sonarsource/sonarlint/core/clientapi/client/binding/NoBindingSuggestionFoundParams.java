package org.sonarsource.sonarlint.core.clientapi.client.binding;

import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class NoBindingSuggestionFoundParams {

  @NonNull
  private final String projectKey;

  public NoBindingSuggestionFoundParams(String projectKey) {
    this.projectKey = projectKey;
  }

  public String getProjectKey() {
    return projectKey;
  }

}
