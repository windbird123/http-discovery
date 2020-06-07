package com.github.windbird123.http.discovery

import scalaj.http.Http
import zio.{Task, UIO}

object BlockingSmartClientTest {
  def main(args: Array[String]): Unit = {
    val addressDiscover = new AddressDiscover.Service {
      override def fetch(): Task[Seq[String]] =
        UIO(Seq("https://jsonplaceholder.typicode.com"))
    }

    val retryPolicy = new RetryPolicy.Service {
      override val waitUntilServerIsAvailable: Boolean                            = true
      override val retryAfterSleepMs: Long                                        = 10000L
      override def isWorthRetry(statusCode: Int, body: Array[Byte]): UIO[Boolean] = UIO.succeed(false)
    }

    val client       = BlockingSmartClient.create(600L, addressDiscover)
    val (code, body) = client.execute(Http("/todos/1").timeout(2000, 2000), retryPolicy)
    println(code)
    println(new String(body, io.Codec.UTF8.name))
  }
}
