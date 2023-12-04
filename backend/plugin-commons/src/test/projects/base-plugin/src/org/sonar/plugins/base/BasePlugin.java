package org.sonar.plugins.base;

import java.util.Collections;
import java.util.List;
import org.sonar.api.Plugin;
import org.sonar.api.Plugin.Context;

public class BasePlugin implements Plugin {
  @Override
  public void define(Context context) {
    // no extensions
  }
}
