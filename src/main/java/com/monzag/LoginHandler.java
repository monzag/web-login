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
import java.util.UUID;

public class LoginHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String response;
        String method = httpExchange.getRequestMethod();
        HttpCookie cookie = getCookie(httpExchange);

        if (cookie != null) {
            if (method.equals("GET")) {
                response = displayHello(cookie);
            } else {
                response = logout(cookie, httpExchange);
            }

        } else {
            if (method.equals("GET")) {
                response = displayLoginFormula();
            } else {
                response = login(httpExchange);
            }
        }

        final byte[] finalResponseBytes = response.getBytes("UTF-8");
        httpExchange.sendResponseHeaders(200, finalResponseBytes.length);
        OutputStream os = httpExchange.getResponseBody();
        os.write(finalResponseBytes);
        os.close();
    }

    public HttpCookie getCookie(HttpExchange httpExchange) {
        String cookieStr = httpExchange.getRequestHeaders().getFirst("Cookie");
        HttpCookie cookie = null;
        if (cookieStr != null) {  // Cookie already exists
            cookie = HttpCookie.parse(cookieStr).get(0);
        }

        return cookie;
    }

    public HttpCookie createCookie(HttpExchange httpExchange, String login) {
        // Create a new cookie
        String sessionId = UUID.randomUUID().toString();
        HttpCookie cookie = new HttpCookie("sessionId", sessionId);
        httpExchange.getResponseHeaders().add("Set-Cookie", cookie.toString());
        this.addCookieToDb(cookie, login);

        return cookie;
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
            HttpCookie cookie = createCookie(httpExchange, login);
            return displayHello(cookie);
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

    public String displayHello(HttpCookie cookie) {
        JtwigTemplate template = JtwigTemplate.classpathTemplate("templates/content.twig");
        JtwigModel model = JtwigModel.newModel();

        String login = getLoginByCookie(cookie);
        model.with("login", login);

        return template.render(model);
    }

    public void addCookieToDb(HttpCookie cookie, String login) {
        String query = "INSERT INTO `cookies` VALUES (?, ?);";

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:src/main/resources/database.db");
             PreparedStatement pstmt = c.prepareStatement(query);) {

            pstmt.setString(1, cookie.getValue());
            pstmt.setString(2, login);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

    }

    public String logout(HttpCookie cookie, HttpExchange httpExchange) {
        removeCookie(cookie, httpExchange);
        return displayLoginFormula();
    }

    public void removeCookie(HttpCookie cookie, HttpExchange httpExchange) {
        String sessionId = cookie.getValue();

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:src/main/resources/database.db");
             Statement stmt = c.createStatement()) {


            String query = String.format("DELETE FROM `cookies` WHERE sessionId = '%s'; ",
                    sessionId);

            stmt.executeUpdate(query);

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        String newCookie = "sessionId='';max-age=0";
        httpExchange.getResponseHeaders().add("Set-Cookie", newCookie);
    }

    public String getLoginByCookie(HttpCookie cookie) {
        String sessionId = cookie.getValue();
        String query = "SELECT (login) FROM `cookies` WHERE sessionId = '" + sessionId + "';";

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:src/main/resources/database.db");
             Statement stmt = c.createStatement()) {

            ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) {
                System.out.println("login by cookie:" + rs.getString("login"));
                return rs.getString("login");
            }

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return "";
    }
}
