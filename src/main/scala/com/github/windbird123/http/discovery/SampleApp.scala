package com.github.windbird123.http.discovery

import scalaj.http.Http
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.random.Random

object MainService {
  val logic: ZIO[Console with Blocking with Clock with Has[RetryPolicy.Service] with Random with Has[
    AddressDiscover.Service
  ], Throwable, Unit] =
    for {
      client       <- SmartClient.create("url", 600L)
      (code, body) <- client.execute(Http("/todos/1").timeout(2000, 2000))
      _            <- console.putStrLn(code.toString)
      _            <- console.putStrLn(new String(body, io.Codec.UTF8.name))
    } yield ()
}

object SampleApp extends zio.App {
  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] = {
    val layer = AddressDiscover.live ++ RetryPolicy.live
    MainService.logic.tapError(x => UIO(x.printStackTrace())).provideCustomLayer(layer).exitCode
  }
}
