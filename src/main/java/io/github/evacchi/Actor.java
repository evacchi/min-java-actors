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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import static java.lang.System.out;

public interface Actor {
    interface Behavior extends Function<Object, Effect> {}
    interface Effect extends Function<Behavior, Behavior> {}
    interface Address { Address tell(Object msg); }

    static Effect Become(Behavior like) { return old -> like; }
    static Effect Stay = old -> old;
    static Effect Die = Become(msg -> { out.println("Dropping msg [" + msg + "] due to severe case of death."); return Stay; });

    record System(ExecutorService executorService) {
        public Address actorOf(Function<Address, Behavior> initial) {
            abstract class AtomicRunnableAddress implements Address, Runnable
                { final AtomicInteger on = new AtomicInteger(0); }
            var addr = new AtomicRunnableAddress() {
                final ConcurrentLinkedQueue<Object> mb = new ConcurrentLinkedQueue<>();
                Behavior behavior = m -> (m instanceof Address self) ? Become(initial.apply(self)) : Stay;
                public Address tell(Object msg) { mb.offer(msg); async(); return this; }
                public void run() {
                    try { if (on.get() == 1) { var m = mb.poll(); if (m!=null) { behavior = behavior.apply(m).apply(behavior); } }}
                    finally { on.set(0); async(); }}
                void async() {
                    if (!mb.isEmpty() && on.compareAndSet(0, 1)) {
                        try { executorService.submit(this); }
                        catch (Throwable t) { on.set(0); throw t; }}}
            };
            return addr.tell(addr); // Make the actor self aware by seeding its address to the initial behavior
        }
    }

}
