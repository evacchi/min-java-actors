//JAVA 17
//JAVAC_OPTIONS --enable-preview --release 17
//JAVA_OPTIONS  --enable-preview
//SOURCES ../../TypedActor.java
package io.github.evacchi.typed.examples;

import io.github.evacchi.TypedActor;

import java.util.concurrent.Executors;

import static io.github.evacchi.TypedActor.*;
import static java.lang.System.out;

public interface PingPong {

    sealed interface Pong {}
    record SimplePong(Address<Ping> sender) implements Pong {}
    record DeadlyPong(Address<Ping> sender) implements Pong {}

    record Ping(Address<Pong> sender) {}

    static void main(String... args) {
        var actorSystem = new TypedActor.System(Executors.newCachedThreadPool());
        Address<Ping> ponger = actorSystem.actorOf(self -> msg -> pongerBehavior(self, msg, 0));
        Address<Pong> pinger = actorSystem.actorOf(self -> msg -> pingerBehavior(self, msg));
        ponger.tell(new Ping(pinger));
    }
    static Effect<Ping> pongerBehavior(Address<Ping> self, Ping msg, int counter) {
        if (counter < 10) {
            out.println("ping! 👉");
            msg.sender().tell(new SimplePong(self));
            return Become(m -> pongerBehavior(self, m, counter + 1));
        } else {
            out.println("ping! 💀");
            msg.sender().tell(new DeadlyPong(self));
            return Die();
        }
    }
   static Effect<Pong> pingerBehavior(Address<Pong> self, Pong msg) {
        return switch (msg) {
            case SimplePong p -> {
                out.println("pong! 👈");
                p.sender().tell(new Ping(self));
                yield Stay();
            }
            case DeadlyPong p -> {
                out.println("pong! 😵");
                p.sender().tell(new Ping(self));
                yield Die();
            }
        };
    }

}
