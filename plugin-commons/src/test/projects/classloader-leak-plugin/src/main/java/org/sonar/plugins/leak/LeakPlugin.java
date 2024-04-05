package org.sonar.plugins.leak;

import java.io.IOException;
import org.sonar.api.Plugin;

public class LeakPlugin implements Plugin {
  @Override
  public void define(Context context) {
    // See SLCORE-557
    var resource = this.getClass().getClassLoader().getResource("Hello.txt");
    // https://bugs.java.com/bugdatabase/view_bug?bug_id=JDK-8315993
    try (var conn = resource.openConnection().getInputStream()) {
      conn.readAllBytes();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    // no extensions
  }
}
