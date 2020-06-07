package com.github.windbird123.http.discovery

import scalaj.http.HttpRequest
import zio.{Ref, Runtime, ZLayer}

object BlockingSmartClient {
  val runtime: Runtime[zio.ZEnv] = zio.Runtime.default

  def create(periodSec: Long, addressDiscover: AddressDiscover.Service): BlockingSmartClient = {
    val factory = runtime.unsafeRun(for {
      ref <- Ref.make(Seq.empty[String])
    } yield new AddressFactory(ref))

    runtime.unsafeRunToFuture(
      factory.update(periodSec).provideCustomLayer(ZLayer.succeed(addressDiscover))
    )
    new BlockingSmartClient(factory)
  }
}

class BlockingSmartClient(addressFactory: AddressFactory) {
  val runtime: Runtime[zio.ZEnv] = zio.Runtime.default
  val smartClient: SmartClient   = new SmartClient(addressFactory)

  def execute(
    req: HttpRequest,
    retryPolicy: RetryPolicy.Service
  ): (Int, Array[Byte]) = runtime.unsafeRun(smartClient.execute(req).provideCustomLayer(ZLayer.succeed(retryPolicy)))
}
