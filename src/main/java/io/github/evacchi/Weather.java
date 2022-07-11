//JAVA 19
//JAVAC_OPTIONS --enable-preview --source 17
//JAVA_OPTIONS --enable-preview
//SOURCES Actor.java
package io.github.evacchi;

import java.lang.System;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.Executors;

import static io.github.evacchi.Actor.*;

public class Weather {
    public static void main(String... args) {
        var w = new Weather();
        for (var a : args) { w.req(a); }
    }

    Actor.System system = new Actor.System(Executors.newCachedThreadPool());
    Address client = system.actorOf(self -> this::httpClient);

    void req(String arg) {
        client.tell(arg);
    }
    private Effect httpClient(Object msg) {
        if (msg instanceof String city) {
            system.actorOf(self -> this.httpHandler(self, city));
            return Stay;
        } else {
            System.err.println("Bad argument " + msg);
            return Stay;
        }
    }
    private Behavior httpHandler(Address self, String city) {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .uri(URI.create("https://wttr.in/" + city + "?format=3"))
                .build();
        try {
            var resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            self.tell(resp);
        } catch (Exception e) { self.tell(e); }

        return msg -> switch (msg) {
            case HttpResponse resp -> { System.out.println(resp.body()); yield Die; }
            case Exception e -> { e.printStackTrace(); yield Die; }
            default -> Die;
        };
    }

}
