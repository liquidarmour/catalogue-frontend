/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.cataloguefrontend.events

import akka.actor.ActorSystem
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterAll, FunSpec}
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.language.postfixOps

class UpdateSchedulerSpec extends FunSpec with MockitoSugar with BeforeAndAfterAll {
  import ExecutionContext.Implicits.global

  private val actorSystem           = ActorSystem()
  override protected def afterAll() = Await.ready(actorSystem.terminate(), 5 seconds)

  describe("event read model update") {
    it("should be scheduled for specified intervals") {
      val readModelService = mock[ReadModelService]
      val scheduler        = new UpdateScheduler(actorSystem, readModelService)

      scheduler.startUpdatingEventsReadModel(100 milliseconds)

      verify(readModelService, after(scheduler.initialDelay.toMillis.toInt + 550).atLeast(4)).refreshEventsCache
      verify(readModelService, never()).refreshUmpCache
    }
  }

  describe("ump cache read model update") {
    it("should be scheduled for specified intervals") {
      val readModelService = mock[ReadModelService]
      val scheduler        = new UpdateScheduler(actorSystem, readModelService)

      scheduler.startUpdatingUmpCacheReadModel(100 milliseconds)

      verify(readModelService, after(scheduler.initialDelay.toMillis.toInt + 550).atLeast(4)).refreshUmpCache
      verify(readModelService, never()).refreshEventsCache
    }
  }
}
