package com.github.windbird123.http.discovery

import zio._
import zio.Ref
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.{ DefaultRunnableSpec, ZSpec, _ }

object AddressFactoryTest extends DefaultRunnableSpec {
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("AddressFactory tests")(chooseSuite)

  val chooseSuite = suite("choose")(
    testM("ref element 개수가 1개 일때, 해당 element 가 선택되어야 한다.") {
      for {
        ref     <- Ref.make(Seq("abc"))
        factory = new AddressFactory(ref)
        one     <- factory.choose(false)
      } yield assert(one)(equalTo("abc"))
    }
  )
}
