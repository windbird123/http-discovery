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
  def execute(
    req: HttpRequest,
    blackAddresses: UIO[Seq[String]] = UIO(Seq.empty)
  ): ZIO[Blocking with Clock with Has[RetryPolicy.Service], Throwable, (Int, Array[Byte])] =
    for {
      blacks                     <- blackAddresses
      _                          <- addressFactory.exclude(blacks)
      waitUntilServerIsAvailable <- RetryPolicy.waitUntilServerIsAvailable
      chosen                     <- addressFactory.choose(waitUntilServerIsAvailable)
      request                    = addressFactory.build(chosen, req)
      retryAfterSleepMillis      <- RetryPolicy.retryAfterSleepMillis
      res                        <- tryExecute(request).catchAll(_ => execute(req, UIO(Seq(chosen))).delay(retryAfterSleepMillis.millis))
      (code, body)               = res
      worthRetry                 <- RetryPolicy.isWorthRetry(code, body)
      result                     <- if (worthRetry) execute(req, UIO(Seq(chosen))).delay(retryAfterSleepMillis.millis) else ZIO.succeed(res)
    } yield result

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
