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

package uk.gov.hmrc.cataloguefrontend.service

import cats.data.{EitherT, NonEmptyList, OptionT}
import cats.implicits._
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.cataloguefrontend.actions.UmpAuthenticatedRequest
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, Team, TeamsAndRepositoriesConnector, UserManagementAuthConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.{UmpToken, UmpUnauthorized, UmpUserId}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.DisplayName
import uk.gov.hmrc.http.HeaderCarrier


import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthService @Inject()(
    userManagementAuthConnector  : UserManagementAuthConnector
  , userManagementConnector      : UserManagementConnector
  , teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
  )(implicit val ec: ExecutionContext) {

  import AuthService._

  def authenticate(username: String, password: String)(
    implicit hc: HeaderCarrier): Future[Either[UmpUnauthorized, TokenAndDisplayName]] =
    (for {
       umpAuthData    <- EitherT(userManagementAuthConnector.authenticate(username, password))
       optDisplayName <- EitherT.liftF[Future, UmpUnauthorized, Option[DisplayName]](userManagementConnector.getDisplayName(umpAuthData.userId))
       displayName    =  optDisplayName.getOrElse(DisplayName(umpAuthData.userId.value))
     } yield TokenAndDisplayName(umpAuthData.token, displayName)
    ).value

  /** Check username belongs to teams which own services */
  def authorizeServices[A](
      serviceNames: NonEmptyList[String]
    )(implicit request: UmpAuthenticatedRequest[A]
             , hc     : HeaderCarrier
             ): EitherT[Future, Error, Either[ServiceForbidden, Unit]] =
    for {
      teams <- EitherT.liftF[Future, Error, List[Team]](
                 teamsAndRepositoriesConnector.allTeams.map(_.toList
                   .map(_.copy(repos = Some(Map("Service" -> Seq("zxy-frontend"))))) // TODO delete this
                 )
               )

      //teamServicesMap :: Map[String#TeamName, List[String#ServiceName]]
      teamServicesMap
            = teams.map { team =>
                val teamServiceNames = team.repos.getOrElse(Map.empty).get(RepoType.Service.toString).getOrElse(Seq.empty).toList // List[String]
                (team.name, teamServiceNames)
              }.toMap

      //requiredTeamsForServices :: Map[String#TeamName, List[String#ServiceName]]
      requiredTeamsForServices
            <- EitherT.fromEither[Future](
                 serviceNames.traverse[Either[Error, ?], (String, String)] { serviceName =>
                   teamServicesMap.find { case (teamName, teamServiceNames) => teamServiceNames.contains(serviceName) } match {
                     case None                => Left(Error(s"Couldn't find owning team for service `$serviceName`"))
                     case Some((teamName, _)) => Right((serviceName, teamName))
                   }
                 }
               )
               .map(_.groupBy(_._2).mapValues(_.map(_._1)))

      res   <- requiredTeamsForServices.toList.foldM[EitherT[Future, Error, ?], Either[ServiceForbidden, Unit]](Either.right[ServiceForbidden, Unit](())) { case (acc, (teamName, teamServiceNames)) =>
                 EitherT {
                   userManagementConnector.getTeamMembersFromUMP(teamName)
                      .map {
                        case Left(umpErr)       => Left(Error(s"Failed to lookup team members from ump: $umpErr"))
                        case Right(teamMembers) =>
                          val teamMemberNames = teamMembers.map(_.username).collect { case Some(username) => username }.toList
                          if (teamMemberNames.contains(request.username.value))
                            Right(acc)
                          else acc match {
                            case Right(())                  => Right(Left(ServiceForbidden(teamServiceNames)))
                            case Left(ServiceForbidden(s1)) => Right(Left(ServiceForbidden(teamServiceNames ::: s1)))
                          }
                       }
                 }
               }
      } yield res
}

object AuthService {

  final case class TokenAndDisplayName(
      token      : UmpToken
    , displayName: DisplayName
    )

  case class ServiceForbidden(serviceName: NonEmptyList[String])

  case class Error(error: String)
}
