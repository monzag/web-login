package com.monzag;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtwig.JtwigModel;
import org.jtwig.JtwigTemplate;

import java.io.*;
import java.net.HttpCookie;
import java.net.URLDecoder;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class LoginHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String response = "";
        String method = httpExchange.getRequestMethod();
        String cookieStr = httpExchange.getRequestHeaders().getFirst("Cookie");

        if (method.equals("GET")) {
            if (httpExchange.getRequestURI().getPath().equals("/login")) {
                if (isCookieMatch(cookieStr)) {
                    response = displayHello(cookieStr);
                } else {
                    response = displayLoginFormula();
                }
            }

        }
        if (method.equals("POST")) {
            if (httpExchange.getRequestURI().getPath().equals("/login")) {
                if (isCookieMatch(cookieStr)) {
                    response = logout(cookieStr);
                } else {
                    response = login(httpExchange);
                }
            }
        }

        final byte[] finalResponseBytes = response.getBytes("UTF-8");
        httpExchange.sendResponseHeaders(200, finalResponseBytes.length);
        OutputStream os = httpExchange.getResponseBody();
        os.write(finalResponseBytes);
        os.close();
    }

    public String displayLoginFormula() {
        JtwigTemplate template = JtwigTemplate.classpathTemplate("templates/login.twig");
        JtwigModel model = JtwigModel.newModel();

        String response = template.render(model);

        return response;
    }

    public String login(HttpExchange httpExchange) throws IOException {
        InputStreamReader isr = new InputStreamReader(httpExchange.getRequestBody(), "utf-8");
        BufferedReader br = new BufferedReader(isr);
        String formData = br.readLine();

        System.out.println(formData);
        Map inputs = parseFormData(formData);
        String login = inputs.get("login").toString();
        String password = inputs.get("password").toString();

        if (this.isLoginMatch(login, password)) {
            return this.logIn(httpExchange, login);
        } else {
            return displayLoginFormula();
        }
    }

    private static Map<String, String> parseFormData(String formData) throws UnsupportedEncodingException {
        Map<String, String> map = new HashMap<>();
        String[] pairs = formData.split("&");
        for(String pair : pairs){
            String[] keyValue = pair.split("=");
            String value = new URLDecoder().decode(keyValue[1], "UTF-8");
            map.put(keyValue[0], value);
        }
        return map;
    }

    public boolean isLoginMatch(String login, String password) {

        String query = "SELECT * FROM loginData WHERE login = '" + login +
                "' AND password = '" + password + "';";


        try (Connection c = DriverManager.getConnection("jdbc:sqlite:src/main/resources/database.db");
             Statement stmt = c.createStatement()) {

            ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) {
                return true;
            }

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return false;
    }

    public String logIn(HttpExchange httpExchange, String login) {
        String cookieStr = httpExchange.getRequestHeaders().getFirst("Cookie");
        HttpCookie cookie;
        if (cookieStr != null) {  // Cookie already exists
            cookie = HttpCookie.parse(cookieStr).get(0);


        } else { // Create a new cookie
            String sessionId = String.valueOf(Math.random() * 5) + "!" + login;
            cookie = new HttpCookie("sessionId", sessionId);
            httpExchange.getResponseHeaders().add("Set-Cookie", cookie.toString());
            this.addCookieToDb(cookie);
        }

        return displayHello(cookie.getValue());
    }

    public String displayHello(String cookie) {
        JtwigTemplate template = JtwigTemplate.classpathTemplate("templates/content.twig");
        JtwigModel model = JtwigModel.newModel();

        String login = cookie.split("!")[1];
        model.with("login", login);

        return template.render(model);
    }

    public Boolean isCookieMatch(String cookieStr) {
        if (cookieStr != null) {
            String cookieValue = cookieStr.split("\"")[1];
            String query = "SELECT * FROM `cookies` WHERE sessionId = '" + cookieValue + "';";


            try (Connection c = DriverManager.getConnection("jdbc:sqlite:src/main/resources/database.db");
                 Statement stmt = c.createStatement()) {

                ResultSet rs = stmt.executeQuery(query);
                while (rs.next()) {
                    if (rs.getString("sessionId").equals(cookieValue)) {
                        return true;
                    }
                }

            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
        return false;
    }

    public void addCookieToDb(HttpCookie cookie) {
        String query = "INSERT INTO `cookies` VALUES (?, ?);";

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:src/main/resources/database.db");
             PreparedStatement pstmt = c.prepareStatement(query);) {

            pstmt.setString(1, cookie.getValue());
            pstmt.setString(2, cookie.getValue().split("!")[1]);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

    }

    public String logout(String cookieStr) {
        removeCookieFromDb(cookieStr);
        return displayLoginFormula();
    }

    public void removeCookieFromDb(String cookieStr) {
        HttpCookie cookie = HttpCookie.parse(cookieStr).get(0);
        String sessionId = cookie.getValue();

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:src/main/resources/database.db");
             Statement stmt = c.createStatement()) {


            String query = String.format("DELETE FROM `cookies` WHERE sessionId = '%s'; ",
                    sessionId);

            stmt.executeUpdate(query);



        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        cookie.setMaxAge(0);
    }

//    public String getLoginByCookie(String cookieStr) {
//        System.out.println(cookieStr);
//        if (cookieStr != null) {
//            String cookieValue = cookieStr.split("\"")[1];
//            String query = "SELECT (login) FROM `cookies` WHERE sessionId = '" + cookieValue + "';";
//
//            try (Connection c = DriverManager.getConnection("jdbc:sqlite:src/main/resources/database.db");
//                 Statement stmt = c.createStatement()) {
//
//                ResultSet rs = stmt.executeQuery(query);
//                if (rs.next()) {
//                    System.out.println("login by cookie:" + rs.getString("login"));
//                    return rs.getString("login");
//                }
//
//            } catch (SQLException e) {
//                System.out.println(e.getMessage());
//            }
//        }
//        return "";
//    }


}
