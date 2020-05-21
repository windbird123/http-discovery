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
    req: HttpRequest
  ): ZIO[Blocking with Clock with Has[SmartPolicy.Service], Throwable, (Int, Array[Byte])] = {
    def tryExecute(r: HttpRequest) = {
      val schedule = Schedule.spaced(1.second) && Schedule.recurs(3) && Schedule.doWhile[Throwable] {
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

    val response = for {
      waitUntilServerIsAvailable <- SmartPolicy.waitUntilServerIsAvailable
      retryAfterSleepMillis      <- SmartPolicy.retryAfterSleepMillis

      chosen  <- addressFactory.choose(waitUntilServerIsAvailable)
      request = addressFactory.build(chosen, req)
      res     <- tryExecute(request)
    } yield res

    // TODO: 재시도 할 때 기존 주소 exclude - 2군데 수정, 재시도를 for 문 안으로 넣을수 있지 않을까?
    ZIO.ifM(response.isSuccess)(
      {
        val d = for {
          res          <- response
          (code, body) = res
          worthRetry   <- SmartPolicy.isWorthRetry(code, body)
        } yield worthRetry
        ZIO.ifM(d)(execute(req).delay(1.second), response)
      },
      execute(req).delay(1.second)
    )
  }

}
