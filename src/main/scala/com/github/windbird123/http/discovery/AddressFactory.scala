package com.github.windbird123.http.discovery

import scalaj.http.HttpRequest
import zio._
import zio.clock.Clock
import zio.duration._

import scala.util.Random

object AddressFactory {
  def create(
    discoverUrl: String,
    periodSec: Long = 300L
  ): ZIO[Clock with random.Random with Has[AddressDiscover.Service], Nothing, AddressFactory] =
    for {
      ref      <- Ref.make(Seq.empty[String])
      factory  = new AddressFactory(ref)
      schedule = Schedule.spaced(periodSec.seconds).jittered && Schedule.forever
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
    val randomOne = ref.get.map(addrs => Random.shuffle(addrs).headOption)

    ZIO.ifM(randomOne.map(_.isEmpty && waitUntilServerIsAvailable))(
      choose(waitUntilServerIsAvailable).delay(3.seconds),
      randomOne.map {
        case Some(x) => Right(x)
        case None    => Left(new Exception("Any available address was not found"))
      }.absolve
    )
  }

  def exclude(blacks: Seq[String]): UIO[Unit] = ref.update(x => x.filterNot(blacks.contains))

  def build(chosenBase: String, r: HttpRequest): HttpRequest = r.copy(url = chosenBase + r.url)
}
