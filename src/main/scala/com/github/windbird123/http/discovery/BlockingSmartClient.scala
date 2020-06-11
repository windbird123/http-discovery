package com.github.windbird123.http.discovery

import scalaj.http.HttpRequest
import zio.duration._
import zio.{Ref, Runtime, ZLayer}

object BlockingSmartClient {
  val runtime: Runtime[zio.ZEnv] = zio.Runtime.default

  def create(addressDiscover: AddressDiscover.Service): BlockingSmartClient = {
    val layer = ZLayer.succeed(addressDiscover)
    val factory: AddressFactory = runtime.unsafeRun(for {
      ref <- Ref.make(Seq.empty[String])
      fac = new AddressFactory(ref)
      _   <- fac.fetchAndSet().provideCustomLayer(layer) // 최초 한번은 바로 읽어 초기화
    } yield fac)

    runtime.unsafeRunToFuture(
      factory.scheduleUpdate().delay(1.seconds).provideCustomLayer(layer) // 1초 뒤에 scheduling 등록
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
  ): (Int, Array[Byte]) =
    runtime.unsafeRun(smartClient.execute(req).provideCustomLayer(ZLayer.succeed(retryPolicy) ++ HttpAction.live))
}
