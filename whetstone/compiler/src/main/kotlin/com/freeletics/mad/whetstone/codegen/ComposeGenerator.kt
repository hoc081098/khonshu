package com.freeletics.mad.whetstone.codegen

import com.freeletics.mad.whetstone.Data
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec

internal val Generator.composableName get() = "${data.baseName}Screen"

internal class ComposeGenerator(
    override val data: Data,
) : Generator() {

    internal fun generate(): FunSpec {
        return FunSpec.builder(composableName)
            .addAnnotation(composable)
            .addAnnotation(optInAnnotation())
            .apply {
                if (data.navigation != null) {
                    addParameter("navController", navController)
                }
            }
            .addStatement("val scope = %M()", rememberCoroutineScope)
            .addCode("\n")
            .beginControlFlow("val viewModelProvider = %M<%T>(%T::class) { dependencies, handle -> ", rememberViewModelProvider, data.dependencies, data.parentScope)
            // currentBackStackEntry: external method
            // arguments: external method
            .addStatement("val arguments = navController.currentBackStackEntry!!.arguments ?: %T.EMPTY", bundle)
            .addStatement("%T(dependencies, handle, arguments)", viewModelClassName)
            .endControlFlow()
            .addStatement("val viewModel = viewModelProvider[%T::class.java]", viewModelClassName)
            .addStatement("val component = viewModel.%L", viewModelComponentName)
            .addCode("\n")
            .addCode(composableNavigationSetup())
            .addStatement("val stateMachine = component.%L", data.stateMachine.propertyName)
            .addStatement("val state = stateMachine.state.%M()", collectAsState)
            .beginControlFlow("%L(state.value) { action ->", data.baseName)
            // dispatch: external method
            .addStatement("scope.%M { stateMachine.dispatch(action) }", launch)
            .endControlFlow()
            .build()
    }

    private fun composableNavigationSetup(): CodeBlock {
        if (data.navigation == null) {
            return CodeBlock.of("")
        }
        return CodeBlock.builder()
            .beginControlFlow("%M(scope, navController, component) {", launchedEffect)
            .addStatement("val handler = component.%L", data.navigation.navigationHandler.propertyName)
            .addStatement("val navigator = component.%L", data.navigation.navigator.propertyName)
            .addStatement("handler.%N(scope, navController, navigator)", navigationHandlerHandle)
            .endControlFlow()
            .add("\n")
            .build()
    }
}
