// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.xiboard.data.theme

import com.xiboard.data.theme.model.GeneralStyle
import com.xiboard.util.yaml.Yaml
import com.xiboard.util.yaml.mapping
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

class GeneralStyleTest :
    BehaviorSpec({
        Given("Correct trime.yaml") {
            val theme = loadTheme("trime")

            When("loaded") {
                val generalStyle = theme.generalStyle

                Then("it should not be null") {
                    generalStyle shouldNotBe null
                    generalStyle.autoCaps shouldBe false

                    generalStyle.candidateFont shouldBe listOf("han.ttf")
                }
            }
        }

        Given("Empty trime.yaml") {
            val theme = loadTheme("incorrect")

            When("loaded") {
                val generalStyle = theme.generalStyle

                Then("with default value without exception") {
                    generalStyle.autoCaps shouldBe false
                    generalStyle.candidateBorder shouldBe 0
                    generalStyle.candidateFont shouldBe emptyList()
                    generalStyle.commentPosition shouldBe GeneralStyle.CommentPosition.RIGHT
                    generalStyle.enterLabel shouldNotBe null
                    generalStyle.enterLabel.go shouldBe "go"
                }
            }
        }

        Given("Font list accepts both scalar and sequence YAML values") {
            val theme = loadTheme("trime")
            val inline = Theme.decode(
                Yaml.parseToYamlNode(
                    """
                    name: test
                    style:
                      candidate_font: [han.ttf, latin.ttf]
                      comment_font: comment.ttf
                    """.trimIndent(),
                ).mapping!!,
            )

            Then("legacy scalar and explicit sequence values are both preserved") {
                theme.generalStyle.keyFont shouldBe listOf("symbol.ttf")
                inline.generalStyle.candidateFont shouldBe listOf("han.ttf", "latin.ttf")
                inline.generalStyle.commentFont shouldBe listOf("comment.ttf")
            }
        }
    }) {
    companion object {
        private fun loadTheme(id: String): Theme {
            val file = File("src/test/assets/$id.yaml")
            return Theme.decode(Yaml.parseToYamlNode(file.readText()).mapping!!)
        }
    }
}
