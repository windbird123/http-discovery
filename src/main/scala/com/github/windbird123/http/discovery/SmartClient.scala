package com.github.windbird123.http.discovery

import java.net.SocketTimeoutException

import scalaj.http.HttpRequest
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.random.Random

object SmartClient {
  def create(): ZIO[Clock with Random with Has[AddressDiscover.Service], Nothing, SmartClient] =
    for {
      ref     <- Ref.make(Seq.empty[String])
      factory = new AddressFactory(ref)
      _       <- factory.update().fork // note fork
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
      retryAfterSleepMs          <- RetryPolicy.retryAfterSleepMs
      res                        <- tryOnce(request).catchAll(_ => execute(req, UIO(Seq(chosen))).delay(retryAfterSleepMs.millis))
      (code, body)               = res
      worthRetry                 <- RetryPolicy.isWorthRetry(code, body)
      result                     <- if (worthRetry) execute(req, UIO(Seq(chosen))).delay(retryAfterSleepMs.millis) else ZIO.succeed(res)
    } yield result

  def tryOnce(r: HttpRequest): ZIO[Clock with Blocking, Throwable, (Int, Array[Byte])] = {
    val schedule: Schedule[Clock, Throwable, ((Int, Int), Throwable)] =
      Schedule.spaced(1.second) && Schedule.recurs(3) && Schedule.doWhile[Throwable] {
        case _: SocketTimeoutException => true
        case _                         => false
      }

    blocking.effectBlocking {
      val res = r.asBytes
      (res.code, res.body)
    }.retry(schedule)
  }
}
