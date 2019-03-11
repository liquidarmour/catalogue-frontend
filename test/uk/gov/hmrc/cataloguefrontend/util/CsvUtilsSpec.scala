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

package uk.gov.hmrc.cataloguefrontend.util

import org.scalatest.{Matchers, WordSpec}

class CsvUtilsSpec
  extends WordSpec
     with Matchers {

  "CsvUtilsSpec.toCsv" should {
    "return csv for rows" in {
      val rows = Seq(
        Map("a" -> "1", "b" -> "x", "c" -> "true"),
        Map("a" -> "2", "b" -> "y", "c" -> "false"),
        Map("a" -> "3", "b" -> "z", "c" -> "true"))
      CsvUtils.toCsv(rows) shouldBe """|a,b,c
                                       |1,x,true
                                       |2,y,false
                                       |3,z,true""".stripMargin
    }
  }

  "CsvUtilsSpec.toRows" should {
    "convert case class to rows" in {
      CsvUtils.toRows(Seq(
        Row(a = 1, b = "x", c = true),
        Row(a = 2, b = "y", c = false),
        Row(a = 3, b = "z", c = true))) shouldBe Seq(
          Map("a" -> "1", "b" -> "x", "c" -> "true"),
          Map("a" -> "2", "b" -> "y", "c" -> "false"),
          Map("a" -> "3", "b" -> "z", "c" -> "true"))
    }

    "exclude given fields" in {
      CsvUtils.toRows(Seq(
        Row(a = 1, b = "x", c = true),
        Row(a = 2, b = "y", c = false),
        Row(a = 3, b = "z", c = true)),
        ignoreFields = Seq("b")) shouldBe Seq(
          Map("a" -> "1", "c" -> "true"),
          Map("a" -> "2", "c" -> "false"),
          Map("a" -> "3", "c" -> "true"))
    }
  }
}

case class Row(a: Int, b: String, c: Boolean)