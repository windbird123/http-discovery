package com.github.windbird123.http.discovery

import zio._
import zio.duration._

object SampleApp extends zio.App {

  val myapp = for {
    client   <- SmartClient.create("url", 1L)
    schedule = Schedule.spaced(1.seconds) && Schedule.forever
    res      <- client.request().repeat(schedule).fork
    _        <- ZIO.sleep(5.seconds)
  } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    val layer = AddressDiscover.live ++ SmartPolicy.live
    val app   = myapp.provideCustomLayer(layer)
    app.as(0)
  }
}
