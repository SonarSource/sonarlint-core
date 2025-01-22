package org.sonar.plugins.base;

import org.sonar.api.Plugin;
import org.sonar.api.Plugin.Context;

public class BasePlugin implements Plugin {
  @Override
  public void define(Context context) {
    // no extensions
  }
}
