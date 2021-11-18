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
    sealed interface TPong {}
    static record Ping(TypedActor.Address<TPong> sender) {}
    static record Pong(TypedActor.Address<Ping> sender) implements TPong {}
    static record DeadlyPong(TypedActor.Address<Ping> sender) implements TPong {}

    static void main(String... args) {
        var actorSystem = new TypedActor.System(Executors.newCachedThreadPool());
        var ponger = actorSystem.actorOf((TypedActor.Address<Ping> self) -> (Ping msg) -> pongerBehavior(self, msg, 0));
        var pinger = actorSystem.actorOf((TypedActor.Address<TPong> self) -> (TPong msg) -> pingerBehavior(self, msg));
        ponger.tell(new Ping(pinger));
    }

    static TypedActor.Effect<Ping> pongerBehavior(TypedActor.Address<Ping> self, Ping msg, int counter) {
        return switch (msg) {
            case Ping p && counter < 10 -> {
                out.println("ping! 👉");
                p.sender().tell(new Pong(self));
                yield Become(m -> pongerBehavior(self, m, counter + 1));
            }
            case Ping p -> {
                out.println("ping! 💀");
                p.sender().tell(new DeadlyPong(self));
                yield Die();
            }
        };
    }
   static TypedActor.Effect<TPong> pingerBehavior(TypedActor.Address<TPong> self, TPong msg) {
        return switch (msg) {
            case Pong p -> {
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
