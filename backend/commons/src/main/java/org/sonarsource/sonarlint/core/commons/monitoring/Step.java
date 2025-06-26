package org.sonarsource.sonarlint.core.commons.monitoring;

import io.sentry.ITransaction;
import javax.annotation.Nullable;

public class Step {

  private final String task;
  private final Runnable operation;

  public Step(String task, Runnable operation) {
    this.task = task;
    this.operation = operation;
  }

  public void execute() {
    operation.run();
  }

  public void executeTransaction(ITransaction transaction, @Nullable String description) {
    var span = new Span(transaction.startChild(task, description));
    try {
      operation.run();
      span.finishSuccessfully();
    } catch (Exception exception) {
      span.finishExceptionally(exception);
      throw exception;
    }
  }

}
