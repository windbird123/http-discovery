package com.github.windbird123.http.discovery

import scalaj.http.{ Http, HttpRequest }
import zio.test.{ DefaultRunnableSpec, ZSpec }
import zio._
import zio.clock._
import zio.blocking._
import zio.test._
import zio.test.Assertion._

object SmartClientTest extends DefaultRunnableSpec {
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] = suite("SmartClient Test")(executeSuite)

  val successHttpActionLayer: Layer[Nothing, Has[HttpAction.Service]] = ZLayer.succeed(new HttpAction.Service {
    override def tryExecute(r: HttpRequest, maxRetryNumberWhenTimeout: Int): Task[(Int, Array[Byte])] =
      Task.succeed((200, Array.empty[Byte]))
  })

  val executeSuite = suite("execute")(
    testM("test") {
      val retryPolicy: Layer[Nothing, Has[RetryPolicy.Service]] = ZLayer.succeed(new RetryPolicy.Service {
        override val waitUntilServerIsAvailable: Boolean                                      = false
        override val maxRetryNumberWhenTimeout: Int                                           = 5
        override val retryToAnotherAddressAfterSleepMs: Long                                  = 10000L
        override def isWorthRetryToAnotherAddress(code: Int, body: Array[Byte]): UIO[Boolean] = UIO(false)
      })

      val addressDiscover: Layer[Nothing, Has[AddressDiscover.Service]] = ZLayer.succeed(new AddressDiscover.Service {
        override val periodSec: Long            = 5L
        override def fetch(): Task[Seq[String]] = Task.succeed(Seq.empty[String])
      })

      val layer= retryPolicy ++ addressDiscover ++ successHttpActionLayer

      val fail = (for {
        client <- SmartClient.create()
        failed <- client.execute(Http("/test")).flip
      } yield failed).provideSomeLayer[Clock with Blocking](layer)

      for {
        f <- fail
      } yield assert(f)(equalTo(isSubtype[Throwable](anything)))
    }
  )
}
