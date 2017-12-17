package purity.logging

import purity.PuritySuite
import purity.discipline.arbitrary._

class LoggerSuite extends PuritySuite {

  test("LoggerFunction.log with level All") {
    forAll { lines: List[LogLine] =>
      val console = MutableConsole(LogLevel.AllLevel)
      console.log(lines).unsafeRunSync()
      console.buffer.length shouldEqual lines.length
    }
  }

  test("LoggerFunction.log with level Fatal") {
    forAll { lines: List[LogLine] =>
      val console = MutableConsole(LogLevel.FatalLevel)
      console.log(lines).unsafeRunSync()
      console.buffer.length shouldEqual lines.count {
        case _: LogLine.Fatal => true
        case _ => false
      }
    }
  }

  test("LoggerFunction.log with level Error") {
    forAll { lines: List[LogLine] =>
      val console = MutableConsole(LogLevel.ErrorLevel)
      console.log(lines).unsafeRunSync()
      console.buffer.length shouldEqual lines.count {
        case _: LogLine.Fatal => true
        case _: LogLine.Error => true
        case _ => false
      }
    }
  }

  test("LoggerFunction.log with level Warn") {
    forAll { lines: List[LogLine] =>
      val console = MutableConsole(LogLevel.WarnLevel)
      console.log(lines).unsafeRunSync()
      console.buffer.length shouldEqual lines.count {
        case _: LogLine.Fatal => true
        case _: LogLine.Error => true
        case _: LogLine.Warn => true
        case _ => false
      }
    }
  }

  test("LoggerFunction.log with level Info") {
    forAll { lines: List[LogLine] =>
      val console = MutableConsole(LogLevel.InfoLevel)
      console.log(lines).unsafeRunSync()
      console.buffer.length shouldEqual lines.count {
        case _: LogLine.Debug => false
        case _: LogLine.Trace => false
        case _ => true
      }
    }
  }

  test("LoggerFunction.log with level Debug") {
    forAll { lines: List[LogLine] =>
      val console = MutableConsole(LogLevel.DebugLevel)
      console.log(lines).unsafeRunSync()
      console.buffer.length shouldEqual lines.count {
        case _: LogLine.Trace => false
        case _ => true
      }
    }
  }

  test("LoggerFunction.log with level Off") {
    forAll { lines: List[LogLine] =>
      val console = MutableConsole(LogLevel.OffLevel)
      console.log(lines).unsafeRunSync()
      console.buffer.length shouldEqual 0
    }
  }

  test("LoggerFunction monoid laws: associativity") {
    forAll { lines: List[LogLine] =>
      val buffer = MutableConsole.emptyBuffer
      val a = MutableConsole(LogLevel.FatalLevel, buffer)
      val b = MutableConsole(LogLevel.ErrorLevel, buffer)
      val c = MutableConsole(LogLevel.DebugLevel, buffer)
      val abxc = (a.logger |+| b.logger) |+| c.logger
      val axbc = a.logger |+| (b.logger |+| c.logger)
      val resultA = a.flush(lines.traverse(abxc.log).void)
      val resultB = a.flush(lines.traverse(axbc.log).void)
      resultA shouldEqual resultB
    }
  }
}
