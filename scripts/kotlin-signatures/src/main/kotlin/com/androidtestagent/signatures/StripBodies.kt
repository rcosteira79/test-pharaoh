package com.androidtestagent.signatures

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Removes function bodies from the given Kotlin [source], preserving signatures.
 *
 * Walks the PSI tree (via `kotlin-compiler-embeddable`), locates every
 * [KtNamedFunction] with a body expression, and deletes the body's text range
 * (including the surrounding `{ ... }` for block bodies, or `= expr` for
 * expression bodies).
 *
 * Ranges are applied from highest offset to lowest so earlier deletions do not
 * shift later offsets.
 */
fun stripBodies(source: String): String {
    if (source.isEmpty()) return source

    val disposable = Disposer.newDisposable("kotlin-signatures stripBodies")
    try {
        val configuration =
            CompilerConfiguration().apply {
                put(CommonConfigurationKeys.MODULE_NAME, "kotlin-signatures")
                put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
            }
        val environment =
            KotlinCoreEnvironment.createForProduction(
                disposable,
                configuration,
                EnvironmentConfigFiles.JVM_CONFIG_FILES,
            )
        val psiFactory = PsiFileFactory.getInstance(environment.project)
        val ktFile =
            psiFactory.createFileFromText(
                "input.kt",
                KotlinLanguage.INSTANCE,
                source,
            ) as KtFile

        val ranges = mutableListOf<IntRange>()
        ktFile.accept(
            object : KtTreeVisitorVoid() {
                override fun visitNamedFunction(function: KtNamedFunction) {
                    val body = function.bodyExpression
                    if (body != null) {
                        val range = body.textRange
                        ranges += range.startOffset until range.endOffset
                    }
                    super.visitNamedFunction(function)
                }

                override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
                    val body = accessor.bodyExpression
                    if (body != null) {
                        val range = body.textRange
                        ranges += range.startOffset until range.endOffset
                    }
                    super.visitPropertyAccessor(accessor)
                }

                override fun visitAnonymousInitializer(initializer: KtAnonymousInitializer) {
                    val range = initializer.textRange
                    ranges += range.startOffset until range.endOffset
                    // Entire range is removed, so descending would just queue redundant sub-ranges.
                }

                // NOTE: this visitor covers `=`-style initializers and delegated properties.
                // Custom accessors (`get() = ...` / `set(value) { ... }`) are handled by Task 1.4b.
                override fun visitProperty(property: KtProperty) {
                    val equalsToken = property.equalsToken
                    val initializer = property.initializer
                    if (equalsToken != null && initializer != null) {
                        val rangeStart =
                            property.typeReference?.textRange?.endOffset
                                ?: property.nameIdentifier?.textRange?.endOffset
                                ?: equalsToken.textRange.startOffset
                        val rangeEnd = initializer.textRange.endOffset
                        ranges += rangeStart until rangeEnd
                    }

                    val delegate = property.delegate
                    if (delegate != null) {
                        val byKeyword = delegate.byKeywordNode
                        val rangeStart = byKeyword.startOffset
                        val rangeEnd = delegate.textRange.endOffset
                        ranges += rangeStart until rangeEnd
                    }

                    super.visitProperty(property)
                }

                override fun visitParameter(parameter: KtParameter) {
                    val defaultValue = parameter.defaultValue
                    val equalsToken = parameter.equalsToken
                    if (defaultValue != null && equalsToken != null) {
                        // Widen start to end of the type reference (or name identifier) so the
                        // whitespace before `=` is consumed too, matching visitProperty.
                        val rangeStart =
                            parameter.typeReference?.textRange?.endOffset
                                ?: parameter.nameIdentifier?.textRange?.endOffset
                                ?: equalsToken.textRange.startOffset
                        val rangeEnd = defaultValue.textRange.endOffset
                        ranges += rangeStart..rangeEnd
                    }
                    super.visitParameter(parameter)
                }
            },
        )

        if (ranges.isEmpty()) return source

        val builder = StringBuilder(source)
        // Apply deletions from highest offset to lowest to preserve earlier offsets.
        for (range in ranges.sortedByDescending { it.first }) {
            builder.delete(range.first, range.last + 1)
        }
        return builder.toString()
    } finally {
        Disposer.dispose(disposable)
    }
}
