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

package uk.gov.hmrc.cataloguefrontend.connector

import java.time.LocalDate

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

case class MedianDataPoint(median: Int)

case class DeploymentThroughputDataPoint(
  period: String,
  from: LocalDate,
  to: LocalDate,
  leadTime: Option[MedianDataPoint],
  interval: Option[MedianDataPoint])

case class DeploymentStabilityDataPoint(
  period: String,
  from: LocalDate,
  to: LocalDate,
  hotfixRate: Option[Double],
  hotfixInterval: Option[MedianDataPoint])

case class JobMetricDataPoint(
  period: String,
  from: LocalDate,
  to: LocalDate,
  duration: Option[MedianDataPoint],
  successRate: Option[Double])

case class DeploymentIndicators(
  throughput: Seq[DeploymentThroughputDataPoint],
  stability: Seq[DeploymentStabilityDataPoint])

case class DeploymentsMetricResult(
  period: String,
  from: LocalDate,
  to: LocalDate,
  throughput: Option[Throughput],
  stability: Option[Stability])

case class Throughput(leadTime: Option[MeasureResult], interval: Option[MeasureResult])

case class Stability(hotfixRate: Option[Double], hotfixInterval: Option[MeasureResult])

case class MeasureResult(median: Int)

@Singleton
class IndicatorsConnector @Inject()(
  http          : HttpClient,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {
  private val indicatorsBaseUrl: String = servicesConfig.baseUrl("indicators") + "/api/indicators"

  private implicit val mesureResultFormats              = Json.reads[MeasureResult]
  private implicit val throughputFormats                = Json.reads[Throughput]
  private implicit val stabilityFormats                 = Json.reads[Stability]
  private implicit val deploymentsMetricResultFormats   = Json.reads[DeploymentsMetricResult]
  private implicit val medianDataPointFormats           = Json.reads[MedianDataPoint]
  private implicit val jobExecutionTimeDataPointFormats = Json.reads[JobMetricDataPoint]

  private implicit val httpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse): HttpResponse = response
  }

  def deploymentIndicatorsForTeam(teamName: String)(implicit hc: HeaderCarrier): Future[Option[DeploymentIndicators]] =
    deploymentIndicators(s"/team/$teamName/deployments")

  def deploymentIndicatorsForService(serviceName: String)(
    implicit hc: HeaderCarrier): Future[Option[DeploymentIndicators]] =
    deploymentIndicators(s"/service/$serviceName/deployments")

  private def deploymentIndicators(path: String)(implicit hc: HeaderCarrier): Future[Option[DeploymentIndicators]] = {
    val url                                    = indicatorsBaseUrl + path
    val eventualResponse: Future[HttpResponse] = http.GET[HttpResponse](url)
    eventualResponse
      .map { r =>
        r.status match {
          case 404 => Some(DeploymentIndicators(Nil, Nil))
          case 200 =>
            val deploymentsMetricResults: Seq[DeploymentsMetricResult] = r.json.as[Seq[DeploymentsMetricResult]]
            val (deploymentThroughputs, deploymentStabilities) =
              deploymentsMetricResults.map { dmr =>
                val leadTime   = dmr.throughput.flatMap(x => x.leadTime.map(y => MedianDataPoint.apply(y.median)))
                val interval   = dmr.throughput.flatMap(x => x.interval.map(y => MedianDataPoint.apply(y.median)))
                val hotfixRate = dmr.stability.flatMap(_.hotfixRate)
                val hotfixLeadTime =
                  dmr.stability.flatMap(x => x.hotfixInterval.map(y => MedianDataPoint.apply(y.median)))

                (
                  DeploymentThroughputDataPoint(dmr.period, dmr.from, dmr.to, leadTime, interval),
                  DeploymentStabilityDataPoint(dmr.period, dmr.from, dmr.to, hotfixRate, hotfixLeadTime))

              }.unzip

            Some(DeploymentIndicators(deploymentThroughputs, deploymentStabilities))
        }
      }
      .recover {
        case ex =>
          Logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          None
      }
  }

  def buildIndicatorsForRepository(repositoryName: String)(
    implicit hc: HeaderCarrier): Future[Option[Seq[JobMetricDataPoint]]] =
    buildIndicators(s"/repository/$repositoryName/builds")

  private def buildIndicators(path: String)(implicit hc: HeaderCarrier): Future[Option[Seq[JobMetricDataPoint]]] = {
    val url                                    = indicatorsBaseUrl + path
    val eventualResponse: Future[HttpResponse] = http.GET[HttpResponse](url)
    eventualResponse
      .map { r =>
        r.status match {
          case 404 => Some(Nil)
          case 200 => Some(r.json.as[Seq[JobMetricDataPoint]])
        }
      }
      .recover {
        case ex =>
          Logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          None
      }
  }
}
