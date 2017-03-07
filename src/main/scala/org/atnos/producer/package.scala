package org.atnos

import cats.{Eval, Id, Monoid, Semigroup}
import cats.implicits._
import org.atnos.eff._
import org.atnos.eff.all._
import org.atnos.eff.syntax.safe._
import org.atnos.origami.Fold

package object producer {

  type Transducer[R, A, B] = Producer[R, A] => Producer[R, B]

  object producers extends Producers

  object transducers extends Transducers

  implicit class ProducerOps[R :_Safe, A](p: Producer[R, A]) {
    def filter(f: A => Boolean): Producer[R, A] =
      Producer.filter(p)(f)

    def sliding(n: Int): Producer[R, List[A]] =
      Producer.sliding(n)(p)

    def chunk(n: Int): Producer[R, A] =
      Producer.chunk(n)(p)

    def >(p2: Producer[R, A]): Producer[R, A] =
      p append p2

    def |>[B](t: Transducer[R, A, B]): Producer[R, B] =
      pipe(t)

    def pipe[B](t: Transducer[R, A, B]): Producer[R, B] =
      Producer.pipe(p, t)

    def into[U](implicit intoPoly: IntoPoly[R, U], s: _Safe[U]): Producer[U, A] =
      Producer.into(p)

    def fold[B, S](start: Eff[R, S], f: (S, A) => Eff[R, S], end: S => Eff[R, B]): Eff[R, B] =
      Producer.fold(p)(start, f, end)

    def to[B](f: Fold[Eff[R, ?], A, B]): Eff[R, B] =
      fold[B, f.S](f.start, f.fold, f.end)

    def fold[B](f: Fold[Id, A, B]): Eff[R, B] =
      to(f.into[Eff[R, ?]])

    def foldLeft[S](init: S)(f: (S, A) => S): Eff[R, S] =
      Producer.fold(p)(Eff.pure(init), (s: S, a: A) => pure(f(s, a)), (s: S) => pure(s))

    def foldMonoid(implicit m: Monoid[A]): Eff[R, A] =
      foldLeft(Monoid[A].empty)(Monoid[A].combine)

    def observe[S](start: Eff[R, S], f: (S, A) => Eff[R, S], end: S => Eff[R, Unit]): Producer[R, A] =
      Producer.observe(p)(start, f, end)

    def observe(f: Fold[Eff[R, ?], A, Unit]): Producer[R, A] =
      observe[f.S](f.start, f.fold, f.end)

    def runLast: Eff[R, Option[A]] =
      Producer.runLast(p)

    def drain: Eff[R, Unit] =
      Producer.runLast(p).as(())

    def runList: Eff[R, List[A]] =
      Producer.runList(p)

    def repeat: Producer[R, A] =
      Producer.repeat(p)

    def andFinally(last: Eff[R, Unit]): Producer[R, A] =
      p.andFinally(last)

  }

  implicit class ProducerListOps[R :_Safe, A](p: Producer[R, List[A]]) {
    def flattenList: Producer[R, A] =
      Producer.flattenList(p)
  }

  implicit class ProducerSeqOps[R :_Safe, A](p: Producer[R, Seq[A]]) {
    def flattenSeq: Producer[R, A] =
      Producer.flattenSeq(p)
  }

  implicit class ProducerFlattenOps[R :_Safe, A](p: Producer[R, Producer[R, A]]) {
    def flatten: Producer[R, A] =
      Producer.flatten(p)
  }

  implicit class ProducerEffOps[R :_Safe, A](p: Producer[R, Eff[R, A]]) {
    def sequence[F[_]](n: Int)(implicit f: F |= R): Producer[R, A] =
      Producer.sequence[R, F, A](n)(p)
  }

  implicit class ProducerTransducerOps[R :_Safe, A](p: Producer[R, A]) {
    def receiveOr[B](f: A => Producer[R, B])(or: =>Producer[R, B]): Producer[R, B] =
      p |> transducers.receiveOr(f)(or)

    def receiveOption[B]: Producer[R, Option[A]] =
      p |> transducers.receiveOption

    def drop(n: Int): Producer[R, A] =
      p |> transducers.drop(n)

    def dropRight(n: Int): Producer[R, A] =
      p |> transducers.dropRight(n)

    def take(n: Int): Producer[R, A] =
      p |> transducers.take(n)

    def takeWhile(f: A => Boolean): Producer[R, A] =
      p |> transducers.takeWhile(f)

    def zipWithPrevious: Producer[R, (Option[A], A)] =
      p |> transducers.zipWithPrevious

    def zipWithNext: Producer[R, (A, Option[A])] =
      p |> transducers.zipWithNext

    def zipWithPreviousAndNext: Producer[R, (Option[A], A, Option[A])] =
      p |> transducers.zipWithPreviousAndNext

    def zipWithIndex: Producer[R, (A, Int)] =
      p |> transducers.zipWithIndex

     def intersperse(a: A): Producer[R, A] =
       p |> transducers.intersperse(a: A)

    def first: Producer[R, A] =
      p |> transducers.first

    def last: Producer[R, A] =
      p |> transducers.last

    def scan[B](start: B)(f: (B, A) => B): Producer[R, B] =
      p |> transducers.scan(start)(f)

    def scan1(f: (A, A) => A): Producer[R, A] =
      p |> transducers.scan1(f)

    def reduce(f: (A, A) => A): Producer[R, A] =
      p |> transducers.reduce(f)

    def reduceSemigroup(implicit semi: Semigroup[A]): Producer[R, A] =
      p |> transducers.reduceSemigroup

    def reduceMonoid(implicit monoid: Monoid[A]): Producer[R, A] =
      p |> transducers.reduceMonoid

    def reduceMap[B : Monoid](f: A => B): Producer[R, B] =
      p |> transducers.reduceMap[R, A, B](f)

    def mapEval[B](f: A => Eff[R, B]): Producer[R, B] =
      p |> transducers.mapEval[R, A, B](f)
  }

  implicit class TransducerOps[R :_Safe, A, B](t: Transducer[R, A, B]) {
    def |>[C](next: Transducer[R, B, C]): Transducer[R, A, C] =
      (p: Producer[R, A]) => next(t(p))

    def filter(predicate: B => Boolean): Transducer[R, A, B] = (producer: Producer[R, A]) =>
      t(producer).filter(predicate)
  }

  implicit class ProducerResourcesOps[R :_Safe, A](p: Producer[R, A]) {
    def `finally`(e: Eff[R, Unit]): Producer[R, A] =
      p.andFinally(e)

    def attempt: Producer[R, Throwable Either A] =
      Producer[R, Throwable Either A](SafeInterpretation.attempt(p.run) map {
        case Right(Done()) => Done()
        case Right(One(a)) => One(Either.right(a))
        case Right(More(as, next)) => More(as.map(Either.right), next.map(Either.right))
        case Left(t) => One(Either.left(t))
      })
  }

  def bracket[R :_Safe, A, B, C](open: Eff[R, A])(step: A => Producer[R, B])(close: A => Eff[R, C]): Producer[R, B] =
    Producer[R, B] {
      open flatMap { resource =>
        (step(resource) `finally` close(resource).map(_ => ())).run
      }
    }
}
