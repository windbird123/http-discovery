package com.github.windbird123.http.discovery

import zio._

object AddressDiscover {
  trait Service {
    def fetch(discoverUrl: String): Task[String]
    def parse(text: String): Task[Seq[String]]
  }

  def fetch(discoverUrl: String): ZIO[Has[Service], Throwable, String] = ZIO.accessM(_.get[Service].fetch(discoverUrl))
  def parse(text: String): ZIO[Has[Service], Throwable, Seq[String]] =
    ZIO.accessM(_.get[Service].parse(text))

  val live: Layer[Nothing, Has[Service]] = ZLayer.succeed(
    new Service {
      override def fetch(discoverUrl: String): Task[String] = UIO("url")

      override def parse(text: String): Task[Seq[String]] = UIO(Seq("1"))
    }
  )
}
