package com.github.windbird123.http.discovery

import java.net.{SocketException, SocketTimeoutException}

import com.typesafe.scalalogging.LazyLogging
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

class SmartClient(addressFactory: AddressFactory) extends LazyLogging {
  def execute(
    req: HttpRequest,
    blackAddresses: UIO[Seq[String]] = UIO(Seq.empty)
  ): ZIO[Blocking with Clock with Has[RetryPolicy.Service], Throwable, (Int, Array[Byte])] =
    for {
      blacks                            <- blackAddresses
      _                                 <- addressFactory.exclude(blacks)
      waitUntilServerIsAvailable        <- RetryPolicy.waitUntilServerIsAvailable
      chosen                            <- addressFactory.choose(waitUntilServerIsAvailable)
      request                           = addressFactory.build(chosen, req)
      retryToAnotherAddressAfterSleepMs <- RetryPolicy.retryToAnotherAddressAfterSleepMs
      maxRetryNumberWhenTimeout         <- RetryPolicy.maxRetryNumberWhenTimeout
      res <- tryOnce(request, maxRetryNumberWhenTimeout).catchSome {
              case _: SocketException => execute(req, UIO(Seq(chosen))).delay(retryToAnotherAddressAfterSleepMs.millis)
              case t: Throwable       => ZIO.fail(t)  // SocketTimeoutException 은 여기에 해당함
            }
      (code, body) = res
      worthRetry   <- RetryPolicy.isWorthRetryToAnotherAddress(code, body)
      result <- if (worthRetry) execute(req, UIO(Seq(chosen))).delay(retryToAnotherAddressAfterSleepMs.millis)
               else ZIO.succeed(res)
    } yield result

  def tryOnce(
    r: HttpRequest,
    maxRetryNumberWhenTimeout: Int
  ): ZIO[Clock with Blocking, Throwable, (Int, Array[Byte])] = {
    val schedule: Schedule[Clock, Throwable, ((Duration, Int), Throwable)] = {
      Schedule.exponential(1.second) && Schedule.recurs(maxRetryNumberWhenTimeout) && Schedule.doWhile[Throwable] {
        case e: SocketTimeoutException => {
          logger.info(s"Retry, url=[${r.url}] cause=[${e.getMessage}]")
          true
        }
        case t: Throwable => {
          logger.info(s"Fail, url=[${r.url}]", t)
          false
        }
      }
    }

    blocking.effectBlocking {
      val res = r.asBytes
      (res.code, res.body)
    }.retry(schedule)
  }
}
