package com.github.windbird123.http.discovery

import zio._
import zio.clock.Clock
import zio.console.Console
import zio.duration._

object AddressDiscovery {

  trait Service {
//    def setAddresses(ref: Ref[Seq[String]], addresses: Seq[String]) = ref.set(addresses)
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
  def updateAddr(ref: Ref[Seq[String]]): ZIO[Has[AddressDiscovery.Service], Throwable, Unit] =
    for {
      s    <- AddressDiscovery.fetch("abc")
      addr <- AddressDiscovery.parse(s, "svc")
      _    <- ref.set(addr)
    } yield ()

  def printRef(ref: Ref[Seq[String]]) =
    for {
      ss <- ref.get
      _  <- console.putStrLn(ss.mkString(","))
    } yield ()

  val myapp: ZIO[Console with Clock with Has[AddressDiscovery.Service], Nothing, Unit] = for {
    ref           <- Ref.make(Seq.empty[String])
    schedule      = Schedule.spaced(2.seconds) && Schedule.forever
    schedulePrint = Schedule.spaced(1.seconds) && Schedule.forever
    _             <- updateAddr(ref).repeat(schedule).fork
    _             <- printRef(ref).repeat(schedulePrint).fork
    _             <- ZIO.sleep(10.seconds)
  } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    val layer = AddressDiscovery.live
    val app   = myapp.provideCustomLayer(layer)
    app.as(0)
  }
}
