package com.github.windbird123.http.discovery

import scalaj.http.Http
import zio._

object AddressDiscover {
  trait Service {
    // blocking.effectBlocking 으로 구현되면 좋으나 ZIO[Blocking, Throwable, String] 타입이 되어버림.
    def fetch(discoverUrl: String): Task[String] = Task.effect(Http(discoverUrl).timeout(10000, 10000).asString.body)
    def parse(text: String): Task[Seq[String]]
  }

  def fetch(discoverUrl: String): ZIO[Has[Service], Throwable, String] = ZIO.accessM(_.get[Service].fetch(discoverUrl))
  def parse(text: String): ZIO[Has[Service], Throwable, Seq[String]] =
    ZIO.accessM(_.get[Service].parse(text))

  val live: Layer[Nothing, Has[Service]] = ZLayer.succeed(
    new Service {
      override def fetch(discoverUrl: String): Task[String] = UIO("some url list")

      override def parse(text: String): Task[Seq[String]] = UIO(Seq("https://jsonplaceholder.typicode.com"))
    }
  )
}
