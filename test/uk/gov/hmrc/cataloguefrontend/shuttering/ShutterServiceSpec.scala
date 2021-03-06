/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.shuttering

import java.time.Instant
import java.time.temporal.ChronoUnit

import org.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.connector.RouteRulesConnector
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class ShutterServiceSpec extends AnyWordSpec with MockitoSugar with Matchers {

  val mockShutterStates = Seq(
      ShutterState(
        name        = "abc-frontend"
      , shutterType = ShutterType.Frontend
      , environment = Environment.Production
      , status      = ShutterStatus.Shuttered(reason = None, outageMessage = None, useDefaultOutagePage = false)
      )
    , ShutterState(
        name        = "zxy-frontend"
      , shutterType = ShutterType.Frontend
      , environment = Environment.Production
      , status      = ShutterStatus.Unshuttered
      )
    , ShutterState(
        name        = "ijk-frontend"
      , shutterType = ShutterType.Frontend
      , environment = Environment.Production
      , status      = ShutterStatus.Shuttered(reason = None, outageMessage = None, useDefaultOutagePage = false)
      )
    )

  val mockEvents = Seq(
      ShutterStateChangeEvent(
          username    = "test.user"
        , timestamp   = Instant.now().minus(2, ChronoUnit.DAYS)
        , serviceName = "abc-frontend"
        , environment = Environment.Production
        , shutterType = ShutterType.Frontend
        , status      = ShutterStatus.Shuttered(reason = None, outageMessage = None, useDefaultOutagePage = false)
        , cause       = ShutterCause.UserCreated
        )
    , ShutterStateChangeEvent(
          username    = "fake.user"
        , timestamp   = Instant.now()
        , serviceName = "zxy-frontend"
        , environment = Environment.Production
        , shutterType = ShutterType.Frontend
        , status      = ShutterStatus.Unshuttered
        , cause       = ShutterCause.UserCreated
        )
    , ShutterStateChangeEvent(
          username    = "test.user"
        , timestamp   = Instant.now().minus(1, ChronoUnit.DAYS)
        , serviceName = "ijk-frontend"
        , environment = Environment.Production
        , shutterType = ShutterType.Frontend
        , status      = ShutterStatus.Shuttered(reason = None, outageMessage = None, useDefaultOutagePage = false)
        , cause       = ShutterCause.UserCreated
        )
    )

  "findCurrentStates" should {
    "return a list of shutter events ordered by shutter status" in {
      val boot = Boot.init
      implicit val hc = new HeaderCarrier()

      when(boot.mockShutterConnector.shutterStates(ShutterType.Frontend, Environment.Production))
        .thenReturn(Future(mockShutterStates))
      when(boot.mockShutterConnector.latestShutterEvents(ShutterType.Frontend, Environment.Production))
        .thenReturn(Future(mockEvents))

      val Seq(a,b,c) = Await.result(boot.shutterService.findCurrentStates(ShutterType.Frontend, Environment.Production), Duration(10, "seconds"))

      a.status shouldBe ShutterStatus.Shuttered(reason = None, outageMessage = None, useDefaultOutagePage = false)
      b.status shouldBe ShutterStatus.Shuttered(reason = None, outageMessage = None, useDefaultOutagePage = false)
      c.status shouldBe ShutterStatus.Unshuttered
    }
  }

  "toOutagePageStatus" should {
    val boot = Boot.init

    "handle missing OutagePage" in {
      boot.shutterService.toOutagePageStatus(
          serviceNames = Seq("service1")
        , outagePages  = List.empty
        ) shouldBe List(OutagePageStatus(
            serviceName = "service1"
          , warning     = Some(( "No templatedMessage Element no outage-page"
                               , "Default outage page will be displayed."
                              ))
          ))
    }

    "handle multiple missing OutagePages" in {
      boot.shutterService.toOutagePageStatus(
          serviceNames = Seq("service1", "service2")
        , outagePages  = List.empty
        ) shouldBe List(
            OutagePageStatus(
                serviceName = "service1"
              , warning     = Some(( "No templatedMessage Element no outage-page"
                                   , "Default outage page will be displayed."
                                  ))
              )
          , OutagePageStatus(
                serviceName = "service2"
              , warning     = Some(( "No templatedMessage Element no outage-page"
                                   , "Default outage page will be displayed."
                                  ))
              )
          )
    }

    "handle warnings" in {
      boot.shutterService.toOutagePageStatus(
          serviceNames = Seq("service1", "service2", "service3")
        , outagePages  = List(
                             mkOutagePage(
                                 serviceName = "service1"
                               , warnings    = List(OutagePageWarning(
                                                   name    = "UnableToRetrievePage"
                                                 , message = "Unable to retrieve outage-page from Github"
                                                 ))

                               )
                           , mkOutagePage(
                                 serviceName = "service2"
                               , warnings    = List(OutagePageWarning(
                                                   name    = "MalformedHTML"
                                                 , message = "The outage page was found to have some malformed html content"
                                                 ))
                               )
                           , mkOutagePage(
                                 serviceName = "service3"
                               , warnings    = List(OutagePageWarning(
                                                   name    = "DuplicateTemplateElementIDs"
                                                 , message = "More than one template ID was found in the content for: templatedMessage"
                                                 ))
                               )
                           )
        ) shouldBe List(
            OutagePageStatus(
                serviceName = "service1"
              , warning     = Some(( "Unable to retrieve outage-page from Github"
                                   , "Default outage page will be displayed."
                                  ))
              )
          , OutagePageStatus(
                serviceName = "service2"
              , warning     = Some(( "The outage page was found to have some malformed html content"
                                   , "Outage page will be sent as is, without updating templates."
                                  ))
              )
          , OutagePageStatus(
                serviceName = "service3"
              , warning     = Some(( "More than one template ID was found in the content for: templatedMessage"
                                   , "All matching elements will be updated"
                                  ))
              )
          )
    }
  }

  def mkOutagePage(serviceName: String, warnings: List[OutagePageWarning]): OutagePage =
    OutagePage(
        serviceName      = serviceName
      , environment      = Environment.Production
      , outagePageURL    = ""
      , warnings         = warnings
      , templatedElements = List.empty
      )

  case class Boot(shutterService: ShutterService, mockShutterConnector: ShutterConnector)

  object Boot {
    def init: Boot = {
      val mockShutterConnector       = mock[ShutterConnector]
      val mockShutterGroupsConnector = mock[ShutterGroupsConnector]
      val routeRulesConnector        = mock[RouteRulesConnector]
      val shutterService             = new ShutterService(mockShutterConnector, mockShutterGroupsConnector, routeRulesConnector)
      Boot(shutterService, mockShutterConnector)
    }
  }
}
