package com.github.windbird123.http.discovery

import scalaj.http.HttpRequest
import zio._
import zio.clock.Clock
import zio.duration._
import zio.random.Random

class AddressFactory(ref: Ref[Seq[String]]) {
  def update(
    discoverUrl: String,
    periodSec: Long = 300L
  ): ZIO[Clock with Random with Has[AddressDiscover.Service], Throwable, (Int, Int)] = {
    val schedule = Schedule.spaced(periodSec.seconds).jittered && Schedule.forever
    (for {
      text <- AddressDiscover.fetch(discoverUrl)
      addr <- AddressDiscover.parse(text)
      _    <- ref.set(addr)
    } yield ()).repeat(schedule)
  }

  def choose(waitUntilServerIsAvailable: Boolean): ZIO[Clock, Throwable, String] =
    for {
      oneOpt <- ref.get.map(addrs => scala.util.Random.shuffle(addrs).headOption)
      one <- oneOpt match {
              case Some(x) => ZIO.succeed(x)
              case None =>
                if (waitUntilServerIsAvailable) {
                  // 사용할 주소가 하나도 없을 경우 5초 뒤에 다시 시도
                  choose(waitUntilServerIsAvailable).delay(5.seconds)
                } else {
                  ZIO.fail(new Exception("Any available address was not found"))
                }
            }
    } yield one

  def exclude(blacks: Seq[String]): UIO[Unit] = ref.update(x => x.filterNot(blacks.contains))

  def build(chosenBase: String, r: HttpRequest): HttpRequest = r.copy(url = chosenBase + r.url)
}
