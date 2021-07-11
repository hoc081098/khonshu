package com.freeletics.mad.screens.codegen

import com.freeletics.mad.screens.ScreenData
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.TypeSpec

internal class ComposeFragmentGenerator(
    override val scopeClass: ClassName,
    override val data: ScreenData,
) : Generator() {

    private val composeFragmentClassName = ClassName("${scopeClass.simpleName}Fragment")

    internal fun generate(): TypeSpec {
        return TypeSpec.classBuilder(composeFragmentClassName)
            .superclass(fragment)
            .addFunction(composeOnCreateViewFun())
            .build()
    }

    private fun composeOnCreateViewFun(): FunSpec {
        return FunSpec.builder("onCreateView")
            .addModifiers(OVERRIDE)
            .addParameter("inflater", layoutInflater)
            .addParameter("container", viewGroup.copy(nullable = true))
            .addParameter("savedInstanceState", bundle.copy(nullable = true))
            .returns(view)
            .apply {
                if (data.navigation != null) {
                    addStatement("val navController = %M()", findNavController)
                }
            }
            // requireContext: external method
            .addStatement("val composeView = %T(requireContext())", composeView)
            // setContent: external method
            .beginControlFlow("composeView.setContent {")
            .apply {
                if (data.navigation != null) {
                    addStatement("%L(navController)", composableName)
                } else {
                    addStatement("%L()", composableName)
                }
            }
            .endControlFlow()
            .addStatement("return composeView")
            .build()
    }
}
