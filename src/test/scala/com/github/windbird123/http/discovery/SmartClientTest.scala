package com.github.windbird123.http.discovery

import java.net.{SocketException, SocketTimeoutException}
import java.util.concurrent.atomic.AtomicInteger

import scalaj.http.{Http, HttpRequest}
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.random.Random
import zio.test.Assertion._
import zio.test.environment.TestClock
import zio.test.{DefaultRunnableSpec, ZSpec, _}

object SmartClientTest extends DefaultRunnableSpec {
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] = suite("SmartClient Test")(executeSuite)

  val successHttpActionLayer: Layer[Nothing, Has[HttpAction.Service]] = ZLayer.succeed(new HttpAction.Service {
    override def tryExecute(r: HttpRequest, maxRetryNumberWhenTimeout: Int): Task[(Int, Array[Byte])] =
      Task.succeed((200, "success".getBytes(io.Codec.UTF8.name)))
  })

  val executeSuite = suite("execute")(
    testM("waitUntilServerIsAvailable=false 이고 사용 가능한 주소가 없을때 fail 되어야 한다.") {
      val addressDiscover: Layer[Nothing, Has[AddressDiscover.Service]] = ZLayer.succeed(new AddressDiscover.Service {
        override def fetch(configString: String): Task[Seq[String]] = Task.succeed(Seq.empty[String])
      })

      val retryPolicy: Layer[Nothing, Has[RetryPolicy.Service]] = ZLayer.succeed(new RetryPolicy.Service {
        override val waitUntilServerIsAvailable: Boolean = false
      })

      val layer = retryPolicy ++ addressDiscover ++ successHttpActionLayer

      val scn = for {
        client <- SmartClient.create()
        failed <- client.execute(Http("/some/path")).flip
      } yield failed

      for {
        r <- scn.provideCustomLayer(layer)
      } yield assert(r)(isSubtype[Throwable](anything))
    },
    testM("waitUntilServerIsAvailable=true 이면, 사용 가능한 주소가 있을 때 까지 기다렸다 수행하도록 한다.") {
      val addressDiscover: Layer[Nothing, Has[AddressDiscover.Service]] = ZLayer.succeed(new AddressDiscover.Service {
        val tryCount                 = new AtomicInteger(0)
        override val periodSec: Long = 1
        override def fetch(configString: String): Task[Seq[String]] =
          if (tryCount.getAndIncrement() < 3) Task.succeed(Seq.empty[String]) else Task.succeed(Seq("http://a.b.c"))
      })

      val retryPolicy: Layer[Nothing, Has[RetryPolicy.Service]] = ZLayer.succeed(new RetryPolicy.Service {
        override val retryToAnotherAddressAfterSleepMs: Long = 1000L
        override val waitUntilServerIsAvailable: Boolean     = true
      })

      val layer = retryPolicy ++ addressDiscover ++ successHttpActionLayer

      val scn = for {
        client  <- SmartClient.create()
        resFork <- client.execute(Http("/some/path")).fork
        _       <- TestClock.adjust(5.seconds)
        res     <- resFork.join
      } yield res

      for {
        r <- scn.provideSomeLayer[TestClock with Blocking with Clock with Random](layer)
      } yield assert(r._1)(equalTo(200)) && assert(r._2)(equalTo("success".getBytes(io.Codec.UTF8.name)))
    },
    testM("SocketException 을 발생시키는 bad address 가 있으면, 이를 제거해 나가면서 request 를 시도한다.") {
      val addressDiscover: Layer[Nothing, Has[AddressDiscover.Service]] = ZLayer.succeed(new AddressDiscover.Service {
        override def fetch(configString: String): Task[Seq[String]] =
          Task.succeed(
            Seq(
              "http://bad1",
              "http://bad2",
              "http://bad3",
              "http://bad4",
              "http://bad5",
              "http://bad6",
              "http://bad7",
              "http://bad8",
              "http://bad9",
              "http://good"
            )
          )
      })

      val retryPolicy: Layer[Nothing, Has[RetryPolicy.Service]] = ZLayer.succeed(new RetryPolicy.Service {
        override val maxRetryNumberWhenTimeout: Int          = 1
        override val retryToAnotherAddressAfterSleepMs: Long = 1000L
      })

      val httpAction = ZLayer.succeed(new HttpAction.Service {
        override def tryExecute(r: HttpRequest, maxRetryNumberWhenTimeout: Int): Task[(Int, Array[Byte])] =
          r.url match {
            case s if s.startsWith("http://good") => ZIO.succeed((200, "success".getBytes(io.Codec.UTF8.name)))
            case _                                => ZIO.fail(new SocketException("bad"))
          }
      })

      val layer = retryPolicy ++ addressDiscover ++ httpAction

      val scn = for {
        client  <- SmartClient.create()
        resFork <- client.execute(Http("/some/path")).fork
        _       <- TestClock.adjust(100.seconds)
        res     <- resFork.join
      } yield res

      for {
        r <- scn.provideSomeLayer[TestClock with Blocking with Clock with Random](layer)
      } yield assert(r._1)(equalTo(200)) && assert(r._2)(equalTo("success".getBytes(io.Codec.UTF8.name)))
    },
    testM("HttpAction 의 tryExecute 의 최종 결과가 SocketTimeoutException 일 경우, request 문제로 보고 fail 되어야 한다.") {
      val addressDiscover: Layer[Nothing, Has[AddressDiscover.Service]] = ZLayer.succeed(new AddressDiscover.Service {
        override def fetch(configString: String): Task[Seq[String]] = Task.succeed(Seq("http://a.b.c"))
      })

      val retryPolicy: Layer[Nothing, Has[RetryPolicy.Service]] = ZLayer.succeed(new RetryPolicy.Service {
        override val maxRetryNumberWhenTimeout: Int          = 5
        override val retryToAnotherAddressAfterSleepMs: Long = 1000L
      })

      val httpAction = ZLayer.succeed(new HttpAction.Service {
        override def tryExecute(r: HttpRequest, maxRetryNumberWhenTimeout: Int): Task[(Int, Array[Byte])] =
          ZIO.fail(new SocketTimeoutException())
      })

      val layer = retryPolicy ++ addressDiscover ++ httpAction

      val scn = for {
        client  <- SmartClient.create()
        resFork <- client.execute(Http("/some/path")).fork
        _       <- TestClock.adjust(100.seconds)
        res     <- resFork.join
      } yield res

      for {
        r <- scn.flip.provideSomeLayer[TestClock with Blocking with Clock with Random](layer)
      } yield assert(r)(isSubtype[SocketTimeoutException](anything))
    },
    testM("isWorthRetryToAnotherAddress 에서 설정된 정책대로 retry 가 잘 수행되어야 한다.") {
      val addressDiscover: Layer[Nothing, Has[AddressDiscover.Service]] = ZLayer.succeed(new AddressDiscover.Service {
        override def fetch(configString: String): Task[Seq[String]] =
          Task.succeed(
            Seq(
              "http://bad1",
              "http://bad2",
              "http://bad3",
              "http://bad4",
              "http://bad5",
              "http://bad6",
              "http://bad7",
              "http://bad8",
              "http://bad9",
              "http://good"
            )
          )
      })

      val retryPolicy: Layer[Nothing, Has[RetryPolicy.Service]] = ZLayer.succeed(new RetryPolicy.Service {
        override val maxRetryNumberWhenTimeout: Int          = 1
        override val retryToAnotherAddressAfterSleepMs: Long = 1000L
        override def isWorthRetryToAnotherAddress(code: Int, body: Array[Byte]): UIO[Boolean] =
          if (code == 503) UIO(true) else UIO(false)
      })

      val httpAction = ZLayer.succeed(new HttpAction.Service {
        override def tryExecute(r: HttpRequest, maxRetryNumberWhenTimeout: Int): Task[(Int, Array[Byte])] =
          r.url match {
            case s if s.startsWith("http://good") => ZIO.succeed((200, "success".getBytes(io.Codec.UTF8.name)))
            case _                                => ZIO.succeed((503, Array.empty[Byte]))
          }
      })

      val layer = retryPolicy ++ addressDiscover ++ httpAction

      val scn = for {
        client  <- SmartClient.create()
        resFork <- client.execute(Http("/some/path")).fork
        _       <- TestClock.adjust(100.seconds)
        res     <- resFork.join
      } yield res

      for {
        r <- scn.provideSomeLayer[TestClock with Blocking with Clock with Random](layer)
      } yield assert(r._1)(equalTo(200)) && assert(r._2)(equalTo("success".getBytes(io.Codec.UTF8.name)))
    }
  )
}
