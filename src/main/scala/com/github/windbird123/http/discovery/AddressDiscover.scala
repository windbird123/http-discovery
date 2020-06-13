package com.github.windbird123.http.discovery

import zio._

object AddressDiscover {
  trait Service {
    val periodSec: Long = 300L
    def fetch(): Task[Seq[String]]
  }

  def periodSec: ZIO[Has[Service], Nothing, Long]        = ZIO.access(_.get[Service].periodSec)
  def fetch(): ZIO[Has[Service], Throwable, Seq[String]] = ZIO.accessM(_.get[Service].fetch())

  val live: Layer[Nothing, Has[Service]] = ZLayer.succeed(
    new Service {
      override val periodSec: Long = 300L
      override def fetch(): Task[Seq[String]] =
        UIO(Seq("https://jsonplaceholder.typicode.com"))
    }
  )
}
