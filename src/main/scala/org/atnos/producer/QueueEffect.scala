package org.atnos.producer

import java.util.concurrent._

import org.atnos.eff._
import all._
import future._
import SubscribeEffect._

import scala.collection.mutable.ListBuffer
import scala.concurrent.Promise

case class Queue[+A](name: String)(val maxSize: Int) {
  private val elements = new ArrayBlockingQueue[Any](maxSize)
  private val callbacks = new ListBuffer[Callback[Any]]

  private[producer] def enqueue(a: Any): Unit = synchronized {
    if (callbacks.nonEmpty)
      callbacks.head(Right(a))
    else
      elements.put(a)
  }

  private[producer] def dequeue(callback: Callback[Any]): Unit = synchronized {
    if (elements.size() > 0)
      callback(Right(elements.take))
    else
      callbacks.append(callback)
  }
}

object Queue {

  def create[A](name: String, maxSize: Int = 10): Queue[A] =
    Queue(name)(maxSize)
}

object QueueEffect {
  type _queue[R] = QueueOp |= R

  def enqueue[R :_queue, A](queue: Queue[A], a: A): Eff[R, Unit] =
    send[QueueOp, R, Unit](Enqueue(queue, a))

  def dequeue[R :_queue, A](queue: Queue[A]): Eff[R, A] =
    send[QueueOp, R, A](Dequeue(queue))

  def runQueueFuture[R, U, A](e: Eff[R, A])(implicit m: Member.Aux[QueueOp, R, U],
                                            f: Member[TimedFuture, U]): Eff[U, A] = {

    interpret.translate(e)(new Translate[QueueOp, U] {
      def apply[X](tx: QueueOp[X]): Eff[U, X] =
        tx match {
          case Enqueue(queue, v) =>
            futureDelay[U, Unit](queue.enqueue(v))

          case Dequeue(queue) =>
            val promise = Promise[Any]
            val callback = (ta: Throwable Either Any) => {
              ta.fold(promise.failure, promise.success); ()
            }

            futureDelay[U, Any](queue.dequeue(callback)).flatMap(_ => fromFuture(promise.future).asInstanceOf[Eff[U, X]])
        }
    })
  }
}

sealed trait QueueOp[A]

case class Enqueue[A](queue: Queue[A], value: A) extends QueueOp[Unit]
case class Dequeue[A, R](queue: Queue[A]) extends QueueOp[A]


