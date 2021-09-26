/*
Copyright 2021 Edoardo Vacchi
 
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
 
       http://www.apache.org/licenses/LICENSE-2.0
 
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

/*
 Inspired by Viktor Klang's minscalaactors.scala
 https://gist.github.com/viktorklang/2362563
*/

//JAVA 17+

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
    abstract class AtomicRunnableAddress implements Address, Runnable { AtomicInteger on = new AtomicInteger(0); }
    record System(ExecutorService executorService) {
        public Address actorOf(Function<Address, Behavior> initial) {              // Seeded by the self-reference that yields the initial behavior
            var addr = new AtomicRunnableAddress() { // Memory visibility of "behavior" is guarded by "on" using volatile piggybacking
                private final ConcurrentLinkedQueue<Object> mbox = new ConcurrentLinkedQueue<>();                           // Our awesome little mailbox, free of blocking and evil
                private Behavior behavior =
                        m -> { if (m instanceof Address self) return new Become(initial.apply(self)); else return Stay;}; // Rebindable top of the mailbox, bootstrapped to identity
                public void send(Object msg) {  // As an optimization, we peek at our threads local copy of our behavior to see if we should bail out early
                    if (behavior == Die.like()) { Die.like.apply(msg); }                  // Efficiently bail out if we're _known_ to be dead
                    else { mbox.offer(msg); async(); }    // Enqueue the message onto the mailbox and try to schedule for execution
                }
                public void run() {
                    try { if (on.get() == 1) behavior = behavior.apply(mbox.poll()).apply(behavior); }
                    finally { on.set(0); async(); } // Switch ourselves off, and then see if we should be rescheduled for execution
                }
                void async() {
                    if (!mbox.isEmpty() && on.compareAndSet(0, 1))         // If there's something to process, and we're not already scheduled
                        try { executorService.execute(this); } catch (Throwable t) { on.set(0); throw t; } // Schedule to run on the Executor and back out on failure
                }
            };
            addr.send(addr); // Make the actor self aware by seeding its address to the initial behavior
            return addr;
        }
    }
    static void main(String... args) {
        var actorSystem = new Actor.System(Executors.newCachedThreadPool());
        var actor = actorSystem.actorOf(self -> msg -> { out.println("self: " + self + " got msg " + msg); return Actor.Die; });
        actor.send("foo");
        actor.send("foo");
    }
}
  