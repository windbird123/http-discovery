package com.github.windbird123.http.discovery

import com.typesafe.scalalogging.LazyLogging
import scalaj.http.HttpRequest
import zio._
import zio.clock.Clock
import zio.duration._
import zio.random.Random

class AddressFactory(ref: Ref[Seq[String]]) extends LazyLogging {
  def fetchAndSet(): ZIO[Has[AddressDiscover.Service], Throwable, Unit] =
    for {
      addr <- AddressDiscover.fetch()
      _    <- ref.set(addr)
      _    <- Task(logger.info(s"Base addresses are updated, addresses=[${addr.mkString(",")}]"))
    } yield ()

  def update(): ZIO[Clock with Random with Has[AddressDiscover.Service], Throwable, Unit] =
    for {
      period   <- AddressDiscover.periodSec
      schedule = Schedule.spaced(period.seconds).jittered && Schedule.forever
      _        <- fetchAndSet().repeat(schedule)
    } yield ()

  def choose(waitUntilServerIsAvailable: Boolean): ZIO[Clock, Throwable, String] =
    for {
      oneOpt <- ref.get.map(addrs => scala.util.Random.shuffle(addrs).headOption)
      one <- oneOpt match {
              case Some(x) => ZIO.succeed(x)
              case None =>
                if (waitUntilServerIsAvailable) {
                  // 사용할 주소가 하나도 없을 경우 5초 뒤에 다시 시도
                  logger.info(
                    s"Any available address was not found, waitUntilServerIsAvailable=[$waitUntilServerIsAvailable]"
                  )
                  choose(waitUntilServerIsAvailable).delay(5.seconds)
                } else {
                  ZIO.fail(
                    new Exception(
                      s"Any available address was not found, waitUntilServerIsAvailable=[$waitUntilServerIsAvailable]"
                    )
                  )
                }
            }
    } yield one

  def exclude(blacks: Seq[String]): UIO[Unit] = {
    logger.info(s"Base addresses=[${blacks.mkString(",")}] are excluded")
    ref.update(x => x.filterNot(blacks.contains))
  }

  def build(chosenBase: String, r: HttpRequest): HttpRequest = r.copy(url = chosenBase + r.url)
}
