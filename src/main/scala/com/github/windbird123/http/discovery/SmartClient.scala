package com.github.windbird123.http.discovery

import java.net.SocketException

import com.typesafe.scalalogging.LazyLogging
import scalaj.http.HttpRequest
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.random.Random

object SmartClient {
  def create(): ZIO[Clock with Blocking with Random with Has[AddressDiscover.Service], Throwable, SmartClient] =
    for {
      ref     <- Ref.make(Seq.empty[String])
      factory = new AddressFactory(ref)
      _       <- factory.fetchAndSet() // 최초 한번은 바로 읽어 초기화
      _       <- factory.scheduleUpdate().fork // note fork
    } yield new SmartClient(factory)
}

class SmartClient(addressFactory: AddressFactory) extends LazyLogging {
  def execute(
    req: HttpRequest,
    blackAddresses: UIO[Seq[String]] = UIO(Seq.empty)
  ): ZIO[Blocking with Clock with Has[RetryPolicy.Service] with Has[HttpAction.Service], Throwable, (Int, Array[Byte])] =
    for {
      blacks                            <- blackAddresses
      _                                 <- addressFactory.exclude(blacks)
      waitUntilServerIsAvailable        <- RetryPolicy.waitUntilServerIsAvailable
      chosen                            <- addressFactory.choose(waitUntilServerIsAvailable)
      request                           = addressFactory.build(chosen, req)
      retryToAnotherAddressAfterSleepMs <- RetryPolicy.retryToAnotherAddressAfterSleepMs
      maxRetryNumberWhenTimeout         <- RetryPolicy.maxRetryNumberWhenTimeout
      res <- HttpAction.tryExecute(request, maxRetryNumberWhenTimeout).catchSome {
              case _: SocketException => execute(req, UIO(Seq(chosen))).delay(retryToAnotherAddressAfterSleepMs.millis)
              case t: Throwable       => ZIO.fail(t) // n 번 SocketTimeoutException 은 여기에 해당하며, client request 에 문제가 있는 것으로 봄
            }
      (code, body) = res
      worthRetry   <- RetryPolicy.isWorthRetryToAnotherAddress(code, body)
      result <- if (worthRetry) execute(req, UIO(Seq(chosen))).delay(retryToAnotherAddressAfterSleepMs.millis)
               else ZIO.succeed(res)
    } yield result
}
