package com.freeletics.mad.whetstone.codegen

import com.freeletics.mad.whetstone.Extra
import com.freeletics.mad.whetstone.Data
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec

internal class FileGenerator(
    private val scopeClass: ClassName,
    private val whetstone: Data,
) {

    private val retainedComponentGenerator = RetainedComponentGenerator(scopeClass, whetstone)
    private val viewModelGenerator = ViewModelGenerator(scopeClass, whetstone)
    private val rendererFragmentGenerator = RendererFragmentGenerator(scopeClass, whetstone)
    private val composeFragmentGenerator = ComposeFragmentGenerator(scopeClass, whetstone)
    private val composeGenerator = ComposeGenerator(scopeClass, whetstone)

    fun generate(): FileSpec {
        val builder = FileSpec.builder(scopeClass.packageName, "Whetstone${scopeClass.simpleName}")
            .addType(retainedComponentGenerator.generate())
            .addType(viewModelGenerator.generate())

        if (whetstone.extra is Extra.Compose) {
            builder.addFunction(composeGenerator.generate())
            if (whetstone.extra.withFragment) {
                builder.addType(composeFragmentGenerator.generate())
            }
        }

        if (whetstone.extra is Extra.Renderer) {
            builder.addType(rendererFragmentGenerator.generate())
        }

        return builder.build()
    }
}
