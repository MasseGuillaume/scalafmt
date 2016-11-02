package org.scalafmt.bootstrap

import scala.language.reflectiveCalls

import scala.util.Failure
import scala.util.Success
import scalaz.\/
import scalaz.\/-
import scalaz.concurrent.Task
import scala.collection.immutable.Nil

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader

import coursier._

class FetchError(errors: Seq[(Dependency, Seq[String])])
    extends Exception(errors.toString())

sealed abstract case class Scalafmt(cli: ScalafmtCli) {

  def format(code: String): String = {
    val in = new ByteArrayInputStream(code.getBytes())
    val out = new ByteArrayOutputStream()
    val err = new ByteArrayOutputStream()
    val workingDirectory = new File("").getAbsolutePath
    cli.main(Array("--stdin"),
                in,
                new PrintStream(out),
                new PrintStream(err),
                workingDirectory)
    new String(out.toByteArray)
  }
}

object Scalafmt {
  def fromVersion(version: String): Either[Throwable, Scalafmt] = {
    val start =
      Resolution(
        Set(
          Dependency(Module("com.geirsson", "scalafmt-cli_2.11"), version)
        )
      )

    val repositories = Seq(
      Cache.ivy2Local,
      MavenRepository("https://repo1.maven.org/maven2")
    )

    val fetch = Fetch.from(repositories, Cache.fetch())
    val resolution = start.process.run(fetch).unsafePerformSync
    val errors: Seq[(Dependency, Seq[String])] = resolution.errors
    if (errors.nonEmpty) Left(new FetchError(errors))
    else {
      val localArtifacts: Seq[FileError \/ File] = Task
        .gatherUnordered(
          resolution.artifacts.map(Cache.file(_).run)
        )
        .unsafePerformSync
      val urls = localArtifacts.collect {
        case \/-(file) => file.toURI.toURL
      }
      val classLoader = new URLClassLoader(urls.toArray, null)
      val reflectiveDynamicAccess = new ReflectiveDynamicAccess(classLoader)
      val loadedClass =
        reflectiveDynamicAccess
          .createInstanceFor[ScalafmtCli]("org.scalafmt.cli.Cli$", Nil)
      loadedClass match {
        case Success(cli) => Right(new Scalafmt(cli) {})
        case Failure(e) => Left(e)
      }
    }
  }
}
object Bootstrap {

  def main(args: Array[String]): Unit = {
    println("BOOSTRAP!")
    val cli = Scalafmt.fromVersion("0.4.9-RC2").right.get
    println(cli)
    println(cli.format("object   A     {    }  // foo"))
  }
}
