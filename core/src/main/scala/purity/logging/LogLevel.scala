package purity.logging

import cats.Order

sealed trait LogLevel

object LogLevel {

  case object AllLevel extends LogLevel

  case object DebugLevel extends LogLevel

  case object ErrorLevel extends LogLevel

  case object FatalLevel extends LogLevel

  case object InfoLevel extends LogLevel

  case object OffLevel extends LogLevel

  case object TraceLevel extends LogLevel

  case object WarnLevel extends LogLevel

  implicit val logLevelOrdering: Ordering[LogLevel] =
    (x: LogLevel, y: LogLevel) ⇒ (x, y) match {
      case (a, b) if a == b ⇒ 0
      case (AllLevel, _)    ⇒ 1
      case (_, AllLevel)    ⇒ -1
      case (DebugLevel, _)  ⇒ 1
      case (_, DebugLevel)  ⇒ -1
      case (ErrorLevel, _)  ⇒ 1
      case (_, ErrorLevel)  ⇒ -1
      case (FatalLevel, _)  ⇒ 1
      case (_, FatalLevel)  ⇒ -1
      case (InfoLevel, _)   ⇒ 1
      case (_, InfoLevel)   ⇒ -1
      case (OffLevel, _)    ⇒ 1
      case (_, OffLevel)    ⇒ -1
      case (TraceLevel, _)  ⇒ 1
      case (_, TraceLevel)  ⇒ -1
    }

  implicit val logLevelOrder: Order[LogLevel] = Order.fromOrdering
}
