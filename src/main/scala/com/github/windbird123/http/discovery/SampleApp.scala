package com.github.windbird123.http.discovery

import scalaj.http.Http
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console

object MainLogic {
  val service
    : ZIO[Console with Blocking with Clock with Has[RetryPolicy.Service] with Has[AddressDiscover.Service], Throwable, Unit] =
    for {
      client       <- SmartClient.create("url", 1L)
      (code, body) <- client.execute(Http("/").timeout(2000, 2000))
      _            <- console.putStrLn(code.toString)
      _            <- console.putStrLn(new String(body, io.Codec.UTF8.name))
    } yield ()
}

object SampleApp extends zio.App {
  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    val layer = AddressDiscover.live ++ RetryPolicy.live
    MainLogic.service.tapError(x => UIO(x.printStackTrace())).fold(_ => 1, _ => 0).provideCustomLayer(layer)
  }
}
