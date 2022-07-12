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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;

import static java.lang.System.out;

public interface LoomActor {
    interface Behavior extends Function<Object, Effect> {}
    interface Effect extends Function<Behavior, Behavior> {}
    interface Address { Address tell(Object msg); }

    static Effect Become(Behavior like) { return old -> like; }
    static Effect Stay = old -> old;
    static Effect Die = Become(msg -> { out.println("Dropping msg [" + msg + "] due to severe case of death."); return Stay; });

    record System() {
        private static ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        // DEMO: the third client doesn't work and threads are actively busy
        // private static ExecutorService executorService = Executors.newFixedThreadPool(5); // clientManager + (2 threads X client)
        public Address actorOf(Function<Address, Behavior> initial) {
            abstract class RunnableAddress implements Address, Runnable { }
            var addr = new RunnableAddress() {
                final LinkedBlockingQueue<Object> mb = new LinkedBlockingQueue<>();
                public Address tell(Object msg) { mb.offer(msg); return this; }
                public void run() {
                    Behavior behavior = initial.apply(this);
                    while (true) {
                        try {
                            Object m = mb.take();
                            behavior = behavior.apply(m).apply(behavior);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            break;
                        }
                    }
                }
            };
            executorService.execute(addr);
            return addr;
        }
    }

}
