package purity.verification

import matryoshka.implicits._
import cats.Functor

trait Spec1T[F[_], A1, B] {

  def post(a1: A1): PropositionT[F, B]

  def verify(a1: A1)(program: A1 => B)(implicit F: Functor[F]): F[(String, Boolean)] =
    F.map(post(a1).check(program(a1))) { result =>
      result.cata(Truth.tracker)
    }
}

trait Spec2T[F[_], A1, A2, B] {

  def post(a1: A1, a2: A2): PropositionT[F, B]

  def verify(a1: A1, a2: A2)(program: (A1, A2) => B)(implicit F: Functor[F]): F[(String, Boolean)] =
    F.map(post(a1, a2).check(program(a1, a2))) { result =>
      result.cata(Truth.tracker)
    }
}