package org.scalafmt

import java.io.InputStream
import java.io.PrintStream

package object bootstrap {
  type ScalafmtCli = {
    def main(args: Array[String],
             in: InputStream,
             out: PrintStream,
             err: PrintStream,
             workingDirectory: String): Unit
  }
}
