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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.WhatsRunningWherePage

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WhatsRunningWhereController @Inject()(
    releasesConnector            : ReleasesConnector
  , teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
  , page                         : WhatsRunningWherePage
  , mcc                          : MessagesControllerComponents
  )(implicit val ec: ExecutionContext
  ) extends FrontendController(mcc){

  def landing: Action[AnyContent] =
    Action.async { implicit request =>
      for {
        form                <- Future.successful(WhatsRunningWhereFilter.form.bindFromRequest)
        profile             =  form.fold( _ => None
                                        , f => for {
                                                 profileType <- f.profileType
                                                 profileName <- f.profileName
                                               } yield Profile(profileType, profileName)
                                        )
        selectedProfileType = form.fold(_ => None, _.profileType).getOrElse(ProfileType.Team)
        ( releases
        , profiles
        )                   <- ( releasesConnector.releases(profile)
                               , releasesConnector.profiles
                               ).mapN { case (r, p) => (r, p) }
        environments        =  releases.flatMap(_.versions.map(_.environment)).distinct.sorted
        profileNames        = profiles.filter(_.profileType == selectedProfileType).map(_.profileName).sorted
      } yield
        Ok(page(environments, releases, selectedProfileType, profileNames, form))
    }
}

case class WhatsRunningWhereFilter(
    profileName    : Option[ProfileName]     = None
  , profileType    : Option[ProfileType]     = None
  , applicationName: Option[ApplicationName] = None
  )

object WhatsRunningWhereFilter {
  private def filterEmptyString(x: Option[String]) = x.filter(_.trim.nonEmpty)

  lazy val form = Form(
    mapping(
      "profile_name"     -> optional(text)
                              .transform[Option[ProfileName]](
                                  _.map(ProfileName.apply)
                                , _.map(_.asString)
                                )
    , "profile_type"     -> optional(text)
                              .transform[Option[ProfileType]](
                                  _.flatMap(s => ProfileType.parse(s).toOption)
                                , _.map(_.asString)
                                )
    , "application_name" -> optional(text)
                              .transform[Option[ApplicationName]](
                                  filterEmptyString(_).map(ApplicationName.apply)
                                , _.map(_.asString)
                                )
    )(WhatsRunningWhereFilter.apply)(WhatsRunningWhereFilter.unapply)
  )
}
