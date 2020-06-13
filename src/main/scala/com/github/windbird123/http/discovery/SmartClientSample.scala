package com.github.windbird123.http.discovery

import scalaj.http.Http
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.random.Random

object MainService {
  val logic: ZIO[Console with Blocking with Clock with Random, Throwable, Unit] = {
    val retryPolicy = new RetryPolicy {
      override val waitUntilServerIsAvailable: Boolean = true
    }

    for {
      client       <- SmartClient.create(AddressDiscover.sample, DefaultHttpAction)
      (code, body) <- client.execute(Http("/todos/1").timeout(2000, 2000), retryPolicy)
      _            <- console.putStrLn(code.toString)
      _            <- console.putStrLn(new String(body, io.Codec.UTF8.name))
    } yield ()
  }
}

object SmartClientSample extends zio.App {
  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] =
    MainService.logic.exitCode
}
