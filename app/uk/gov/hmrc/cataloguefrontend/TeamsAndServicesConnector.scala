/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend

import java.net.URLEncoder

import play.api.libs.json.Json
import uk.gov.hmrc.cataloguefrontend.config.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}

import scala.concurrent.Future

case class Service(name: String, teamNames: Seq[String], githubUrls: Seq[Link], ci: List[Link])
case class Link(name: String, url: String)

trait TeamsAndServicesConnector extends ServicesConfig {
  val http: HttpGet
  val teamsAndServicesBaseUrl: String

  implicit val linkFormats = Json.format[Link]
  implicit val serviceFormats = Json.format[Service]

  def allTeams(implicit hc: HeaderCarrier): Future[CachedList[String]] = {
    http.GET[CachedList[String]](teamsAndServicesBaseUrl + s"/api/teams")
  }

  def teamServices(teamName : String)(implicit hc: HeaderCarrier): Future[CachedList[Service]] = {
    http.GET[CachedList[Service]](teamsAndServicesBaseUrl + s"/api/teams/${URLEncoder.encode(teamName,"UTF-8")}/services")
  }

  def allServices(implicit hc: HeaderCarrier) : Future[CachedList[Service]] = {
    http.GET[CachedList[Service]](teamsAndServicesBaseUrl + s"/api/services")
  }
}

object TeamsAndServicesConnector extends TeamsAndServicesConnector {
  override val http = WSHttp
  override lazy val teamsAndServicesBaseUrl: String = baseUrl("teams-and-services")
}
