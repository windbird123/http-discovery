package com.github.windbird123.http.discovery

import java.net.SocketTimeoutException

import scalaj.http.HttpRequest
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._

object SmartClient {
  def create(
    url: String,
    periodSec: Long
  ): ZIO[Clock with Has[AddressDiscover.Service], Nothing, SmartClient] =
    for {
      factory <- AddressFactory.create(url, periodSec)
    } yield new SmartClient(factory)
}

class SmartClient(addressFactory: AddressFactory) {
  def execute(req: HttpRequest): ZIO[Blocking with Clock with Has[SmartPolicy.Service], Throwable, (Int, Array[Byte])] =
    for {
      waitUntilServerIsAvailable <- SmartPolicy.waitUntilServerIsAvailable
      retryAfterSleepMillis      <- SmartPolicy.retryAfterSleepMillis

      chosen       <- addressFactory.choose(waitUntilServerIsAvailable)
      request      = addressFactory.build(chosen, req)
      res          <- tryExecute(request).catchAll(_ => execute(req).delay(retryAfterSleepMillis.millis))
      (code, body) = res
      worthRetry   <- SmartPolicy.isWorthRetry(code, body)
      out          <- if (worthRetry) execute(req).delay(retryAfterSleepMillis.millis) else ZIO.succeed(res)
    } yield out

  def tryExecute(r: HttpRequest): ZIO[Clock with Blocking, Throwable, (Int, Array[Byte])] = {
    val schedule: Schedule[Clock, Throwable, ((Int, Int), Throwable)] =
      Schedule.spaced(1.second) && Schedule.recurs(3) && Schedule.doWhile[Throwable] {
        case _: SocketTimeoutException => {
          println("TEST Sock timeout")
          true
        }
        case _ => false
      }

    blocking.effectBlocking {
      val res = r.asBytes
      (res.code, res.body)
    }.retry(schedule)
  }

}
