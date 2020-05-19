package com.github.windbird123.http.discovery

import zio._
import zio.clock.Clock
import zio.duration._

object AddressDiscovery {

  trait Service {
    def fetch(url: String): Task[String]
    def parse(json: String, svcName: String): Task[Seq[String]]
  }

  def fetch(url: String): ZIO[Has[Service], Throwable, String] = ZIO.accessM(_.get.fetch(url))
  def parse(json: String, svcName: String): ZIO[Has[Service], Throwable, Seq[String]] =
    ZIO.accessM(_.get.parse(json, svcName))

  val live: Layer[Nothing, Has[Service]] = ZLayer.succeed(
    new Service {
      override def fetch(url: String): Task[String] = UIO("url")

      override def parse(json: String, svcName: String): Task[Seq[String]] = UIO(Seq("1"))
    }
  )
}

object MyApp extends zio.App {
  val myapp = for {
    ref      <- Ref.make(Seq.empty[String])
    util     = new AddrUtil(ref)
    schedule = Schedule.spaced(2.seconds) && Schedule.forever
    _        <- util.updateAddr().repeat(schedule).fork
    _        <- util.choose().repeat(schedule).fork
    _        <- ZIO.sleep(10.seconds)
  } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    val layer = AddressDiscovery.live
    val app   = myapp.provideCustomLayer(layer)
    app.as(0)
  }
}

class AddrUtil(ref: Ref[Seq[String]]) {
  def updateAddr(): ZIO[Has[AddressDiscovery.Service], Throwable, Unit] =
    for {
      s    <- AddressDiscovery.fetch("abc")
      addr <- AddressDiscovery.parse(s, "svc")
      _    <- ref.set(addr)
    } yield ()

  val schedule: Schedule[Clock, Any, (Int, Int)] = Schedule.spaced(2.seconds) && Schedule.forever
  def choose(): ZIO[Clock with Has[AddressDiscovery.Service], Nothing, Option[String]] =
    for {
      _   <- updateAddr().repeat(schedule).fork
      seq <- ref.get
      _   <- UIO(println(seq.headOption))
    } yield seq.headOption
}
