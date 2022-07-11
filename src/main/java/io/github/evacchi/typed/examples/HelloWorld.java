//JAVA 19
//JAVAC_OPTIONS --enable-preview --release 19
//JAVA_OPTIONS  --enable-preview
//SOURCES ../../TypedActor.java

package io.github.evacchi.typed.examples;

import io.github.evacchi.TypedActor;

import java.util.concurrent.Executors;

import static io.github.evacchi.TypedActor.*;
import static java.lang.System.out;


interface HelloWorld {
    static void main(String... args) throws InterruptedException {
        var actorSystem = new TypedActor.System(Executors.newCachedThreadPool());
        Address<String> actor = actorSystem.actorOf(self -> msg -> {
            out.println("self: " + self +"; got msg: '" + msg + "'; length: " + msg.length());
            return TypedActor.Die();
        });
        actor.tell("foo");
        actor.tell("bar");
    }
}
