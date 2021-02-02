package foo;

import java.sql.SQLException;

public class DbHelper {

  static boolean executeQuery(java.sql.Connection connection, String user, String pass) throws SQLException {
    String query = "SELECT * FROM users WHERE user = '" + user + "' AND pass = '" + pass + "'"; // Unsafe

    java.sql.Statement statement = connection.createStatement();
    java.sql.ResultSet resultSet = statement.executeQuery(query); // Noncompliant
    return resultSet.next();
  }

}
