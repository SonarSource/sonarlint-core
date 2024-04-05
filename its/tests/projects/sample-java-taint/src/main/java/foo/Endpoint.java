package foo;

import java.sql.SQLException;

public class Endpoint {

  public boolean authenticate(javax.servlet.http.HttpServletRequest request, java.sql.Connection connection) throws SQLException {
    String user = request.getParameter("user");
    String pass = request.getParameter("pass");

    return DbHelper.executeQuery(connection, user, pass);
  }

}
