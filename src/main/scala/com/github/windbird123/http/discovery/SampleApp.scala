package com.github.windbird123.http.discovery

import scalaj.http.Http
import zio._
import zio.duration._

object SampleApp extends zio.App {

  val myapp = for {
    client   <- SmartClient.create("url", 1L)
    schedule = Schedule.spaced(1.seconds) && Schedule.forever
    res      <- client.execute(Http("/").timeout(1, 1))
    _        <- ZIO.sleep(5.seconds)
  } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    val layer = AddressDiscover.live ++ SmartPolicy.live
    myapp.tapError(x => UIO(x.printStackTrace())).fold(_ => 1, _ => 0).provideCustomLayer(layer)
  }
}
