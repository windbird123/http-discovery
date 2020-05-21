package com.github.windbird123.http.discovery

import scalaj.http.HttpRequest
import zio._
import zio.clock.Clock
import zio.duration._

object AddressFactory {
  def create(
    discoverUrl: String,
    periodSec: Long = 300
  ): ZIO[Clock with Has[AddressDiscover.Service], Nothing, AddressFactory] =
    for {
      ref      <- Ref.make(Seq.empty[String])
      factory  = new AddressFactory(ref)
      schedule = Schedule.spaced(periodSec.seconds) && Schedule.forever
      _        <- factory.update(discoverUrl).repeat(schedule).fork
    } yield factory
}

class AddressFactory(ref: Ref[Seq[String]]) {
  private def update(discoverUrl: String): ZIO[Has[AddressDiscover.Service], Throwable, Unit] =
    for {
      text <- AddressDiscover.fetch(discoverUrl)
      addr <- AddressDiscover.parse(text)
      _    <- ref.set(addr)
    } yield ()

  def choose(
    waitUntilServerIsAvailable: Boolean
  ): ZIO[Clock, Throwable, String] = {
    val head = ref.get.map(_.headOption)

    ZIO.ifM(head.map(_.isEmpty && waitUntilServerIsAvailable))(
      choose(waitUntilServerIsAvailable).delay(3.seconds),
      head.map {
        case Some(x) => Right(x)
        case None    => Left(new Exception("No available address"))
      }.absolve
    )
  }

  def build(chosenBase: String, r: HttpRequest): HttpRequest = r.copy(url = chosenBase + r.url)
}
