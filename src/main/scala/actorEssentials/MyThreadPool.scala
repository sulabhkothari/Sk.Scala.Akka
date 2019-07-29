package actorEssentials

import java.util.concurrent.ConcurrentLinkedQueue

import scala.concurrent.Future

object MyThreadPool {
  def main(args: Array[String]): Unit = {
    val tp = new MyThreadPool
    for (i <- 0 to 99) {
      tp.enqueueTask(() => {
        println(s"New Task: $i !!")
        Thread.sleep(1000)
      })
    }

    Thread.sleep(10000)
    tp.stopAll()
  }
}

class MyThreadPool {

  private val MAX_THREADS: Int = 20
  private var shouldStop = false

  def stopAll() = this.synchronized {
    shouldStop = true
    for (t <- allThreads) t.synchronized {
      t.notify()
    }
  }

  private val allThreads: Array[Thread] = new Array[Thread](MAX_THREADS)
  private val concurrentQueue = new ConcurrentLinkedQueue[() => Unit]
  private val freeThreads: ConcurrentLinkedQueue[Thread] = new ConcurrentLinkedQueue[Thread]

  for (i <- 0 to MAX_THREADS - 1) {
    allThreads(i) = new Thread(() => {
      allThreads(i).synchronized {
        while (!shouldStop) {
          freeThreads.add(allThreads(i))
          allThreads(i).wait()
          if (!shouldStop) {
            println(s"Executor: $i executing ...")
            concurrentQueue.poll().apply()
            println(s"Executor: $i finished !!!")
          }
          else {
            println(s"Executor: $i stopping !!")
          }
        }
      }
    })
    allThreads(i).start()
  }

  def enqueueTask(task: () => Unit) = this.synchronized {
    println(s"Free Executor queue size: ${freeThreads.size()}")
    concurrentQueue.add(task)
    while (freeThreads.size() < 1) {}

    val executor = freeThreads.poll()
    executor.synchronized {
      executor.notify()
    }
  }
}

object ThreadModelLimitations extends App {

  /*
  Daniel's rants
   */

  /**
    * DR #1: OOP encapsulation is only valid in the SINGLE THREADED MODEL.
    */
  class BankAccount(private var amount: Int) {
    override def toString: String = "" + amount

    def withdraw(money: Int) = this.synchronized {
      this.amount -= money
    }

    def deposit(money: Int) = this.synchronized {
      this.amount += money
    }

    def getAmount = amount
  }

  //  val account = new BankAccount(2000)
  //  for(_ <- 1 to 1000) {
  //    new Thread(() => account.withdraw(1)).start()
  //  }
  //
  //  for(_ <- 1 to 1000) {
  //    new Thread(() => account.deposit(1)).start()
  //  }
  //  println(account.getAmount)

  // OOP encapsulation is broken in a multithreaded env
  // synchronization! Locks to the rescue

  // deadlocks, livelocks

  /**
    * DR #2: delegating something to a thread is a PAIN.
    */

  // you have a running thread and you want to pass a runnable to that thread.

  var task: Runnable = null

  val runningThread: Thread = new Thread(() => {
    while (true) {
      while (task == null) {
        runningThread.synchronized {
          println("[background] waiting for a task...")
          runningThread.wait()
        }
      }

      task.synchronized {
        println("[background] I have a task!")
        task.run()
        task = null
      }
    }
  })

  def delegateToBackgroundThread(r: Runnable) = {
    if (task == null) task = r

    runningThread.synchronized {
      runningThread.notify()
    }
  }

  runningThread.start()
  Thread.sleep(1000)
  delegateToBackgroundThread(() => println(42))
  Thread.sleep(1000)
  delegateToBackgroundThread(() => println("this should run in the background"))


  /**
    * DR #3: tracing and dealing with errors in a multithreaded env is a PITN.
    */
  // 1M numbers in between 10 threads
  import scala.concurrent.ExecutionContext.Implicits.global

  val futures = (0 to 9)
    .map(i => 100000 * i until 100000 * (i + 1)) // 0 - 99999, 100000 - 199999, 200000 - 299999 etc
    .map(range => Future {
    if (range.contains(546735)) throw new RuntimeException("invalid number")
    range.sum
  })

  val sumFuture = Future.reduceLeft(futures)(_ + _) // Future with the sum of all the numbers
  sumFuture.onComplete(println)
}