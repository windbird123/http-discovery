package com.github.windbird123.http.discovery

import zio._
import zio.clock.Clock

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
  def request() =
    for {
      waitUntilServerIsAvailable <- SmartPolicy.waitUntilServerIsAvailable
      httpConnectTimeout         <- SmartPolicy.httpConnectTimeout
      retryAfterSleepMillis      <- SmartPolicy.retryAfterSleepMillis

      addr <- addressFactory.choose(waitUntilServerIsAvailable)
      _    <- UIO(println(addr))
    } yield addr
}
