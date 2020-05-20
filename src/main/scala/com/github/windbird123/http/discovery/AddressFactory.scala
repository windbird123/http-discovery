package com.github.windbird123.http.discovery

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
  def update(discoverUrl: String): ZIO[Has[AddressDiscover.Service], Throwable, Unit] =
    for {
      text <- AddressDiscover.fetch(discoverUrl)
      addr <- AddressDiscover.parse(text)
      _    <- ref.set(addr)
    } yield ()

  def choose(
    waitUntilServerIsAvailable: Boolean
  ): ZIO[Clock with Has[AddressDiscover.Service], Nothing, Option[String]] = {
    val head = ref.get.map(_.headOption)

    ZIO.ifM(head.map(_.isEmpty && waitUntilServerIsAvailable))(
      ZIO.sleep(3.seconds) *> choose(waitUntilServerIsAvailable),
      head
    )
  }
}
