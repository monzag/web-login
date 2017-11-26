package com.monzag;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

public class App
{
    public static void main( String[] args ) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8111), 0);

        server.createContext("/login", new LoginHandler());
        server.createContext("/static", new Static());
        server.setExecutor(null);

        server.start();
    }
}
