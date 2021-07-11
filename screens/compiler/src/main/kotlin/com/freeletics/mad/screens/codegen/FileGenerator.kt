package com.freeletics.mad.screens.codegen

import com.freeletics.mad.screens.Extra
import com.freeletics.mad.screens.ScreenData
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec

internal class FileGenerator(
    private val scopeClass: ClassName,
    private val screens: ScreenData,
) {

    private val retainedComponentGenerator = RetainedComponentGenerator(scopeClass, screens)
    private val viewModelGenerator = ViewModelGenerator(scopeClass, screens)
    private val rendererFragmentGenerator = RendererFragmentGenerator(scopeClass, screens)
    private val composeFragmentGenerator = ComposeFragmentGenerator(scopeClass, screens)
    private val composeGenerator = ComposeGenerator(scopeClass, screens)

    fun generate(): FileSpec {
        val builder = FileSpec.builder(scopeClass.packageName, "${scopeClass.simpleName}Screens")
            .addType(retainedComponentGenerator.generate())
            .addType(viewModelGenerator.generate())

        if (screens.extra is Extra.Compose) {
            builder.addFunction(composeGenerator.generate())
            if (screens.extra.withFragment) {
                builder.addType(composeFragmentGenerator.generate())
            }
        }

        if (screens.extra is Extra.Renderer) {
            builder.addType(rendererFragmentGenerator.generate())
        }

        return builder.build()
    }
}
