package com.github.windbird123.http.discovery

import zio._

object RetryPolicy {
  trait Service {
    val waitUntilServerIsAvailable: Boolean                            = true
    val retryAfterSleepMs: Long                                        = 1000L
    def isWorthRetry(statusCode: Int, body: Array[Byte]): UIO[Boolean] = UIO.succeed(false)
  }

  def waitUntilServerIsAvailable: ZIO[Has[Service], Nothing, Boolean] =
    ZIO.access(_.get[Service].waitUntilServerIsAvailable)
  def retryAfterSleepMs: ZIO[Has[Service], Nothing, Long] = ZIO.access(_.get[Service].retryAfterSleepMs)
  def isWorthRetry(statusCode: Int, body: Array[Byte]): ZIO[Has[Service], Throwable, Boolean] =
    ZIO.accessM(_.get[Service].isWorthRetry(statusCode, body))

  val live: Layer[Nothing, Has[Service]] = ZLayer.succeed(new Service {
    override def isWorthRetry(statusCode: Int, body: Array[Byte]): UIO[Boolean] = UIO.succeed(false)
  })
}
