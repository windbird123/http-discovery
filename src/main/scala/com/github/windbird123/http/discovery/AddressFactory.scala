package com.github.windbird123.http.discovery

import com.typesafe.scalalogging.LazyLogging
import scalaj.http.HttpRequest
import zio._
import zio.clock.Clock
import zio.duration._
import zio.random.Random

class AddressFactory(ref: Ref[Seq[String]], addressDiscover: AddressDiscover) extends LazyLogging {
  def fetchAndSet(): Task[Unit] =
    for {
      addr <- addressDiscover.fetch().orElse(ref.get) // 실패할 경우 기존 주소를 그대로 유지한다.
      _    <- ref.set(addr)
      _    <- Task(logger.info(s"Base addresses are updated, addresses=[${addr.mkString(",")}]"))
    } yield ()

  def scheduleUpdate(): ZIO[Clock with Random, Throwable, Unit] =
    for {
      delayFactor <- random.nextDouble
      period      = addressDiscover.periodSec
      schedule    = Schedule.spaced(period.seconds) && Schedule.forever
      _           <- fetchAndSet().repeat(schedule).delay((delayFactor * period).toLong.seconds)
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

  def exclude(blacks: Seq[String]): UIO[Unit] =
    UIO(logger.info(s"Abnormal addresses=[${blacks.mkString(",")}] are excluded")) *> ref.update(x =>
      x.filterNot(blacks.contains)
    )

  def build(chosenBase: String, r: HttpRequest): HttpRequest = r.copy(url = chosenBase + r.url)
}
