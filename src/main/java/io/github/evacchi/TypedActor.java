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

//JAVA 19
//JAVAC_OPTIONS --enable-preview --release 19
//JAVA_OPTIONS  --enable-preview

package io.github.evacchi;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import static java.lang.System.out;

public interface TypedActor {
    interface Effect<T> extends Function<Behavior<T>, Behavior<T>> {}
    interface Behavior<T> extends Function<T, Effect<T>> {}
    interface Address<T> { Address<T> tell(T msg); }
    static <T> Effect<T> Become(Behavior<T> next) { return current -> next; }
    static <T> Effect<T> Stay() { return current -> current; }
    static <T> Effect<T> Die() { return Become(msg -> { out.println("Dropping msg [" + msg + "] due to severe case of death."); return Stay(); }); }
    record System(Executor executor) {
        public <T> Address<T> actorOf(Function<Address<T>, Behavior<T>> initial) {
            abstract class AtomicRunnableAddress<T> implements Address<T>, Runnable
                { AtomicInteger on = new AtomicInteger(0); }
            return new AtomicRunnableAddress<T>() {
                // Our awesome little mailbox, free of blocking and evil
                final ConcurrentLinkedQueue<T> mbox = new ConcurrentLinkedQueue<>();
                Behavior<T> behavior = initial.apply(this);
                public Address<T> tell(T msg) { mbox.offer(msg); async(); return this; }  // Enqueue the message onto the mailbox and try to schedule for execution
                // Switch ourselves off, and then see if we should be rescheduled for execution
                public void run() {
                    try { if (on.get() == 1) { T m = mbox.poll(); if (m != null) behavior = behavior.apply(m).apply(behavior); }
                    } finally { on.set(0); async(); }
                }
                // If there's something to process, and we're not already scheduled
                void async() {
                    if (!mbox.isEmpty() && on.compareAndSet(0, 1)) {
                        // Schedule to run on the Executor and back out on failure
                        try { executor.execute(this); } catch (Throwable t) { on.set(0); throw t; }
                    }
                }
            };
        }
    }

    static void main(String... args) {
        String choice = args.length >= 1? args[0] : "1";
        switch (Integer.parseInt(choice)) {
            case 1, default -> new Demo1().run();
            case 2 -> new Demo2().closure();
            case 3 -> new Demo2().stateful();
            case 4 -> new DemoVending().run();
        }
    }

    class Demo1 {
        void run() {
            var actorSystem = new TypedActor.System(Executors.newCachedThreadPool());
            var actor = actorSystem.actorOf(self -> msg -> {
                out.println("self: " + self + " got msg " + msg);
                return TypedActor.Die();
            });
            actor.tell("foo");
            actor.tell("foo");
        }
    }

    class Demo2 {
        static record Ping(Address<TPong> sender) {}
        sealed interface TPong { }
        static record Pong(Address<Ping> sender) implements TPong {}
        static record DeadlyPong(Address<Ping> sender) implements TPong {}
        void closure() {
            var actorSystem = new TypedActor.System(Executors.newCachedThreadPool());
            var ponger = actorSystem.actorOf((Address<Ping> self) -> (Ping msg) -> pongerBehavior(self, msg, 0));
            var pinger = actorSystem.actorOf((Address<TPong> self) -> (TPong msg) -> pingerBehavior(self, msg));
            ponger.tell(new Ping(pinger));
        }

        void stateful() {
            var actorSystem = new TypedActor.System(Executors.newCachedThreadPool());
            var ponger = actorSystem.actorOf(StatefulPonger::new);
            var pinger = actorSystem.actorOf((Address<TPong> self) -> (TPong msg) -> pingerBehavior(self, msg));
            ponger.tell(new Ping(pinger));
        }

        Effect<Ping> pongerBehavior(Address<Ping> self, Ping msg, int counter) {
            return switch (msg) {
                case Ping p when counter < 10 -> {
                    out.println("ping! ➡️");
                    p.sender().tell(new Pong(self));
                    yield Become(m -> pongerBehavior(self, m, counter + 1));
                }
                case Ping p -> {
                    out.println("ping! ☠️");
                    p.sender().tell(new DeadlyPong(self));
                    yield Die();
                }
            };
        }
        Effect<TPong> pingerBehavior(Address<TPong> self, TPong msg) {
            return switch (msg) {
                case Pong p -> {
                    out.println("pong! ⬅️");
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

        static class StatefulPonger implements Behavior<Ping> {
            Address<Ping> self; int counter = 0;
            StatefulPonger(Address<Ping> self) { this.self = self; }
            public Effect<Ping> apply(Ping msg) {
                return switch (msg) {
                    case Ping p when counter < 10 -> {
                        out.println("ping! ➡️");
                        p.sender().tell(new Pong(self));
                        this.counter++;
                        yield Stay();
                    }
                    case Ping p -> {
                        out.println("ping! ☠️");
                        p.sender().tell(new DeadlyPong(self));
                        yield Die();
                    }
                };
            }
        }
    }

    class DemoVending {

        sealed interface Vend {}
        static record Coin(int amount) implements Vend{
            public Coin {
                if (amount < 1 && amount > 100)
                    throw new AssertionError("1 <= amount < 100");
            }
        }
        static record Choice(String product) implements Vend{}

        void run() {
            var actorSystem = new TypedActor.System(Executors.newCachedThreadPool());
            var vendingMachine = actorSystem.actorOf((Address<Vend> self) -> (Vend msg) -> new DemoVending().initial(msg));
            vendingMachine.tell(new Coin(50));
            vendingMachine.tell(new Coin(40));
            vendingMachine.tell(new Coin(30));
            vendingMachine.tell(new Choice("Chocolate"));
        }

        Effect<Vend> initial(Vend message) {
            return switch(message) {
                case Coin c -> {
                    out.println("Received first coin: " + c.amount);
                    yield Become(m -> waitCoin(m, c.amount()));
                }
                default -> Stay(); // ignore message, stay in this state
            };
        }
        Effect<Vend> waitCoin(Object message, int counter) {
            return switch(message) {
                case Coin c when counter + c.amount() < 100 -> {
                    var count = counter + c.amount();
                    out.println("Received coin: " + count + " of 100");
                    yield Become(m -> waitCoin(m, count));
                }
                case Coin c -> {
                    var count = counter + c.amount();
                    out.println("Received last coin: " + count + " of 100");
                    var change = counter + c.amount() - 100;
                    yield Become(m -> vend(m, change));
                }
                default -> Stay(); // ignore message, stay in this state
            };
        }
        Effect<Vend> vend(Object message, int change) {
            return switch(message) {
                case Choice c -> {
                    vendProduct(c.product());
                    releaseChange(change);
                    yield Become(this::initial);
                }
                default -> Stay(); // ignore message, stay in this state
            };
        }

        void vendProduct(String product) {
            out.println("VENDING: " + product);
        }

        void releaseChange(int change) {
            out.println("CHANGE: " + change);
        }
    }
}
  