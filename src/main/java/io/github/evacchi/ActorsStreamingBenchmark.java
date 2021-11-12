package io.github.evacchi;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jctools.queues.MpscUnboundedArrayQueue;
import org.jctools.queues.MpscUnboundedXaddArrayQueue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
public class ActorsStreamingBenchmark {

   @Param(value = {"JCToolsMpsc", "JCToolsXadd", "CLQ"})
   String mailBoxType;

   Actor.Address consumerActor;

   Actor.System system;

   @Param(value = {"0", "10"})
   int work;

   @Param(value = {"0", "10"})
   int tellDelay;

   private Process process;

   @Setup
   public void createActors(Blackhole bh) {
      final String mailBoxType = this.mailBoxType;
      final Queue<Object> mailBox;
      switch (mailBoxType) {
         case "JCToolsMpsc":
            mailBox = new MpscUnboundedArrayQueue<>(128);
            break;
         case "JCToolsXadd":
            mailBox = new MpscUnboundedXaddArrayQueue<>(128);
            break;
         case "CLQ":
            mailBox = new ConcurrentLinkedQueue<>();
            break;
         default:
            throw new IllegalStateException("Unexpected value: " + mailBoxType);
      }
      system = new Actor.System(Executors.newSingleThreadExecutor(r -> {
         final Thread t = new Thread(r);
         t.setDaemon(true);
         return t;
      }));
      consumerActor = system.actorOf(self -> msg -> consumerBehaviour(msg, bh), mailBox);
      process = new Process(work);
   }

   static record Process(int delay) {

   }

   static record CallMe(Runnable task) {

   }

   static Actor.Effect consumerBehaviour(Object msg, Blackhole bh) {
      return switch (msg) {
         case Process p -> {
            bh.consume(p);
            final int delay = p.delay;
            if (delay > 0) {
               Blackhole.consumeCPU(delay);
            }
            yield Actor.Stay;
         }
         case CallMe c -> {
            c.task.run();
            yield Actor.Stay;
         }
         default -> throw new IllegalStateException("Unexpected value: " + msg);
      };
   }

   @Benchmark
   public void tell() {
      consumerActor.tell(process);
      if (tellDelay > 0) {
         Blackhole.consumeCPU(tellDelay);
      }
   }

   @Benchmark
   @Threads(4)
   public void tellIn4() {
      consumerActor.tell(process);
      if (tellDelay > 0) {
         Blackhole.consumeCPU(tellDelay);
      }
   }

   @TearDown(Level.Iteration)
   public synchronized void emptyQ() {
      final CountDownLatch done = new CountDownLatch(1);
      consumerActor.tell(new CallMe(done::countDown));
      try {
         done.await();
      } catch (Throwable ignore) {

      }
   }
}

