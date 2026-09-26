package com.jarvis.ai

import dev.langchain4j.spi.prompt.PromptTemplateFactory
import java.util.regex.Pattern

/**
 * Custom PromptTemplateFactory for Android to prevent PatternSyntaxException in ICU regex engine.
 * LangChain4j 0.30.0's DefaultPromptTemplateFactory uses Pattern.compile("\\{\\{(.+?)}}"),
 * which fails on Android 13+ (ICU 70+) because closing braces must be escaped when opening braces are.
 */
class AndroidPromptTemplateFactory : PromptTemplateFactory {

    override fun create(input: PromptTemplateFactory.Input): PromptTemplateFactory.Template {
        return AndroidTemplate(input.template)
    }

    private class AndroidTemplate(private val template: String) : PromptTemplateFactory.Template {
        private val allVariables: Set<String> = extractVariables(template)

        override fun render(variables: Map<String, Any>): String {
            ensureAllVariablesProvided(variables)
            var result = template
            for ((key, value) in variables) {
                result = replaceAll(result, key, value)
            }
            return result
        }

        private fun ensureAllVariablesProvided(variables: Map<String, Any>) {
            for (variable in allVariables) {
                if (!variables.containsKey(variable)) {
                    throw IllegalArgumentException("Value for the variable '$variable' is missing")
                }
            }
        }

        private companion object {
            // Android ICU regex compliant pattern with escaped closing braces
            private val VARIABLE_PATTERN: Pattern = Pattern.compile("\\{\\{(.+?)\\}\\}")

            private fun extractVariables(template: String): Set<String> {
                val variables = HashSet<String>()
                val matcher = VARIABLE_PATTERN.matcher(template)
                while (matcher.find()) {
                    matcher.group(1)?.let { variables.add(it) }
                }
                return variables
            }

            private fun replaceAll(inText: String, key: String, value: Any?): String {
                val replacement = value?.toString()
                    ?: throw IllegalArgumentException("Value for the variable '$key' is null")
                return inText.replace("{{$key}}", replacement)
            }
        }
    }
}
