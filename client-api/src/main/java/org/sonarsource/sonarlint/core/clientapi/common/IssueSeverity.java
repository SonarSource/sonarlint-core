package org.sonarsource.sonarlint.core.clientapi.common;

public enum IssueSeverity {

  INFO(1),
  MINOR(2),
  MAJOR(3),
  CRITICAL(4),
  BLOCKER(5);

  private final int value;

  IssueSeverity(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
