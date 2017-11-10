package purity.script

import cats.{ Functor, Monad, MonadError }
import purity.logging.{ LogLine, LoggerFunction }
import purity.script.ScriptT.Definition

import scala.util.control.NoStackTrace

/**
 * This represents a program with dependencies D, domain failures E, and which produces an A. Also it can accumulate logging.
 *
 * The objective of this data type is to achieve monadic, lazy, composable and reasonable computations across our services.
 *
 * You should use the start dependencies, and pure functions to start building a script, the fail function to fail a script,
 * then map, flatMap, inject, mapFailure, adaptFailure to compose Script programs, and on the end of your program you
 * can execute it by using the fold function, which requires the promised dependencies, a function to handle the logging,
 * a function to handle the failures, and a function to handle the produced value, after that you need to handle async
 * computations using the returned F[_].
 *
 * Note that domain failures are supposed to model what can possible go wrong within the domain of the function, lower
 * level exceptions (like network failure) should be handled within the F[_] that the function run returns.
 *
 * @param definition function.
 * @tparam F should be a monad that handles async, like Future, IO, Task, or Streams.
 * @tparam D dependencies of the program.
 * @tparam E domain failures of the program.
 * @tparam A value that the program produces.
 */
case class ScriptT[F[+_], -D, +E, +A](definition: Definition[F, D, E, A])(dsl: ScriptDSL[F]) {

  def map[B](f: A ⇒ B)(implicit F: Functor[F]): ScriptT[F, D, E, B] =
    dsl.map(this)(f)

  def flatMap[B, DD <: D, EE >: E](f: A ⇒ ScriptT[F, DD, EE, B])(implicit M: MonadError[F, Throwable]): ScriptT[F, DD, EE, B] =
    dsl.flatMap[A, B, DD, EE](this)(f)

  /**
   * A normal functor map over the errors E. Useful when composing with another script that has different errors but
   * which you require it's produced value.
   */
  def leftMap[E2](f: E ⇒ E2)(implicit F: Functor[F]): ScriptT[F, D, E2, A] =
    dsl.leftMap(this)(f)

  /**
   * leftMap alias
   */
  def mapFailure[E2](f: E ⇒ E2)(implicit F: Functor[F]): ScriptT[F, D, E2, A] =
    dsl.leftMap(this)(f)

  /**
   * Just like [[MonadError]] `handleErrorWith`, but does a mapping on the failure through the process.
   * Also one may see this as the flatMap of mapFailure (mapFailure creates a Functor, adaptFailure creates a Monad)
   *
   *  Useful for when your original failure E is a coproduct, and you wish to handle just 1 or 2 errors but still keep
   *  others. i.e:
   *  {{{
   *  val failed: Script[D, Either[Int, Int], String] = Script.fail(Left(1))
   *  val handled: Script[D, Int, String] = Script.adaptFailure {
   *    case Left(_) => Script.pure("This is now ok")
   *    case Right(e) => Script.fail(e)
   *  }
   *  }}}
   */
  def recover[E2, DD <: D, AA >: A](f: E ⇒ ScriptT[F, DD, E2, AA])(implicit M: Monad[F]): ScriptT[F, DD, E2, AA] =
    dsl.recover[E, E2, DD, AA](this)(f)

  /**
   * Injects the required dependencies from an upper level container.
   *  {{{
   *  case class AkkaD(system: ActorSystem)
   *  case class Dependencies(akka: AkkaD, uri: String)
   *
   *  val pingActor: Script[AkkaD, Nothing, Ping] = ...
   *
   *  val restOfProgram: Script[Dependencies, Nothing, String] = for {
   *    ping <- pingActor.inject[Dependencies](_.akka)
   *  } yield ping.toString
   *  }}}
   *
   * @param di function that injects dependencies from upper container D2 to local dependencies D
   * @tparam D2 upper dependencies container
   * @return a ScriptT that now instead of requiring dependencies D, now requires dependencies D2
   */
  def contramap[D2](di: D2 ⇒ D): ScriptT[F, D2, E, A] =
    dsl.contramap(this)(di)

  /**
   * contramap alias
   */
  def inject[D2](di: D2 ⇒ D): ScriptT[F, D2, E, A] =
    dsl.contramap(this)(di)

  def logFailure(f: E ⇒ LogLine)(implicit M: Functor[F]): ScriptT[F, D, E, A] =
    dsl.logFailure(this)(f)

  def logError(f: Throwable ⇒ LogLine)(implicit M: MonadError[F, Throwable]): ScriptT[F, D, E, A] =
    dsl.logError(this)(f)

  /**
   * Adds the promised dependencies, runs a logger with the log lines, and removes the failures by adding a function
   * that will handle them. Produces an IO monad that can finally be executed.
   *
   * Note that no actual computation is done until you run the IO monad (see cats effects library).
   *
   * @param dependencies required for the computation.
   * @param logger function with side effects that does the actual logging.
   * @param onFailure handler function.
   * @param onSuccess handler function.
   * @tparam B type which already contains an answer to domain failures, for example the `Result` data type from an http library.
   * @return an IO monad that still needs to be run and handled.
   */
  def fold[B](dependencies: D, logger: LoggerFunction, onFailure: E ⇒ B, onSuccess: A ⇒ B)(implicit F: MonadError[F, Throwable]): F[B] =
    dsl.fold(this)(dependencies, logger, onFailure, onSuccess)

  /** Alias for fold */
  def run[B](dependencies: D, logger: LoggerFunction, onFailure: E ⇒ B, onSuccess: A ⇒ B)(implicit F: MonadError[F, Throwable]): F[B] =
    dsl.fold(this)(dependencies, logger, onFailure, onSuccess)

  /** Same as `run` but the failure and success handlers return an IO instead of a pure value. */
  def foldF[B](dependencies: D, logger: LoggerFunction, onFailure: E ⇒ F[B], onSuccess: A ⇒ F[B])(implicit F: MonadError[F, Throwable]): F[B] =
    dsl.foldF(this)(dependencies, logger, onFailure, onSuccess)

  /** Alias for foldF */
  def runF[B](dependencies: D, logger: LoggerFunction, onFailure: E ⇒ F[B], onSuccess: A ⇒ F[B])(implicit F: MonadError[F, Throwable]): F[B] =
    dsl.foldF(this)(dependencies, logger, onFailure, onSuccess)
}

object ScriptT extends ScriptTInstances {

  type Definition[F[+_], -D, +E, +A] = D ⇒ F[(List[LogLine], Either[E, A])]

  private[purity] final case class ExceptionWithLogs(logs: List[LogLine], e: Throwable) extends RuntimeException with NoStackTrace with Product with Serializable
}

private[purity] trait ScriptTInstances {

  implicit def stdMonadErrorForScript[F[+_], D, E](implicit ev: MonadError[F, Throwable]): MonadError[ScriptT[F, D, E, ?], E] = {
    val dsl: ScriptDSL[F] = new ScriptDSL[F] {}
    new MonadError[ScriptT[F, D, E, ?], E] {
      override def flatMap[A, B](fa: ScriptT[F, D, E, A])(f: (A) ⇒ ScriptT[F, D, E, B]): ScriptT[F, D, E, B] =
        dsl.flatMap(fa)(f)
      override def tailRecM[A, B](a: A)(f: (A) ⇒ ScriptT[F, D, E, Either[A, B]]): ScriptT[F, D, E, B] =
        dsl.tailRecM(a)(f)
      override def pure[A](x: A): ScriptT[F, D, E, A] =
        dsl.pure(x)
      override def raiseError[A](e: E): ScriptT[F, D, E, A] =
        dsl.raiseError(e)
      override def handleErrorWith[A](fa: ScriptT[F, D, E, A])(f: (E) ⇒ ScriptT[F, D, E, A]): ScriptT[F, D, E, A] =
        dsl.handleErrorWith(fa)(f)
    }
  }
}
