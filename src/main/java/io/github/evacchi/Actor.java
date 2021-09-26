/*
 *    Inspired by Viktor Klang's minscalaactors.scala
 *    https://gist.github.com/viktorklang/2362563
 *    Copyright 2014 Viktor Klang
 *
 *    Copyright 2021 Edoardo Vacchi
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

//JAVA 17
//JAVAC_OPTIONS --enable-preview --release 17
//JAVA_OPTIONS  --enable-preview

package io.github.evacchi;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import static java.lang.System.out;

public interface Actor {
    interface Behavior extends Function<Object, Effect> {}
    interface Effect extends Function<Behavior, Behavior> {}
    final record Become(Behavior like) implements Effect { public Behavior apply(Behavior old) { return like; } }
    Effect Stay = old -> old;
    Become Die = new Become(msg -> { out.println("Dropping msg [" + msg + "] due to severe case of death."); return Stay; }); // Stay Dead plz
    interface Address { void send(Object msg); } // The notion of an Address to where you can post messages to
    record System(ExecutorService executorService) {
        public Address actorOf(Function<Address, Behavior> initial) { // Seeded by the self-reference that yields the initial behavior
            abstract class AtomicRunnableAddress implements Address, Runnable { AtomicInteger on = new AtomicInteger(0); }
            var addr = new AtomicRunnableAddress() { // Memory visibility of "behavior" is guarded by "on" using volatile piggybacking
                private final ConcurrentLinkedQueue<Object> mbox = new ConcurrentLinkedQueue<>(); // Our awesome little mailbox, free of blocking and evil
                private Behavior behavior =  m -> { if (m instanceof Address self) return new Become(initial.apply(self)); else return Stay; }; // Rebindable top of the mailbox, bootstrapped to identity
                public void send(Object msg) {  // As an optimization, we peek at our threads local copy of our behavior to see if we should bail out early
                    if (behavior == Die.like()) { Die.like().apply(msg); } // Efficiently bail out if we're _known_ to be dead
                    else { mbox.offer(msg); async(); }  // Enqueue the message onto the mailbox and try to schedule for execution
                }
                public void run() { try { if (on.get() == 1) behavior = behavior.apply(mbox.poll()).apply(behavior); } finally { on.set(0); async(); } } // Switch ourselves off, and then see if we should be rescheduled for execution
                void async() { if (!mbox.isEmpty() && on.compareAndSet(0, 1)) // If there's something to process, and we're not already scheduled
                    { try { executorService.execute(this); } catch (Throwable t) { on.set(0); throw t; } } // Schedule to run on the Executor and back out on failure
                }
            };
            addr.send(addr); // Make the actor self aware by seeding its address to the initial behavior
            return addr;
        }
    }
    static void main(String... args) {
        String choice = args.length >= 1? args[0] : "1";
        switch (Integer.parseInt(choice)) {
            case 1, default -> new Demo1().run();
            case 2 -> new Demo2().run();
        }
    }

    class Demo1 {
        void run() {
            var actorSystem = new Actor.System(Executors.newCachedThreadPool());
            var actor = actorSystem.actorOf(self -> msg -> {
                out.println("self: " + self + " got msg " + msg);
                return Actor.Die;
            });
            actor.send("foo");
            actor.send("foo");
        }
    }

    class Demo2 {
        sealed interface PingPong { Address sender(); }
        static record Ping(Address sender) implements PingPong {}
        static record Pong(Address sender) implements PingPong {}
        static record DeadlyPong(Address sender) implements PingPong {}
        void run() {
            var actorSystem = new Actor.System(Executors.newCachedThreadPool());
            var ponger = actorSystem.actorOf(self -> msg -> pongerBehavior(self, msg, 0));
            var pinger = actorSystem.actorOf(self -> msg -> pingerBehavior(self, msg));
            ponger.send(new Ping(pinger));
        }
        Effect pongerBehavior(Address self, Object msg, int counter) {
            return switch (msg) {
                case Ping p && counter < 10 -> {
                    out.println("ping! ➡️");
                    p.sender().send(new Pong(self));
                    yield new Become(m -> pongerBehavior(self, m, counter + 1));
                }
                case Ping p -> {
                    out.println("ping! ☠️");
                    p.sender().send(new DeadlyPong(self));
                    yield Die;
                }
                default -> Stay;
            };
        }
        Effect pingerBehavior(Address self, Object msg) {
            return switch (msg) {
                case Pong p -> {
                    out.println("pong! ⬅️");
                    p.sender().send(new Ping(self));
                    yield Stay;
                }
                case DeadlyPong p -> {
                    out.println("pong! 😵");
                    p.sender().send(new Ping(self));
                    yield Die;
                }
                default -> Stay;
            };
        }
    }
}
  