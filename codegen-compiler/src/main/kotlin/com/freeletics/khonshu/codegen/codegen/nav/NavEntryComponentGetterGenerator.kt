package com.freeletics.khonshu.codegen.codegen.nav

import com.freeletics.khonshu.codegen.NavEntryData
import com.freeletics.khonshu.codegen.codegen.Generator
import com.freeletics.khonshu.codegen.codegen.common.retainedComponentFactoryCreateName
import com.freeletics.khonshu.codegen.codegen.common.retainedParentComponentClassName
import com.freeletics.khonshu.codegen.codegen.common.retainedParentComponentGetterName
import com.freeletics.khonshu.codegen.codegen.util.InternalCodegenApi
import com.freeletics.khonshu.codegen.codegen.util.context
import com.freeletics.khonshu.codegen.codegen.util.getNavEntryComponent
import com.freeletics.khonshu.codegen.codegen.util.inject
import com.freeletics.khonshu.codegen.codegen.util.internalNavigatorApi
import com.freeletics.khonshu.codegen.codegen.util.navEntryComponentGetter
import com.freeletics.khonshu.codegen.codegen.util.navEntryComponentGetterKey
import com.freeletics.khonshu.codegen.codegen.util.navigationExecutor
import com.freeletics.khonshu.codegen.codegen.util.optInAnnotation
import com.freeletics.khonshu.codegen.codegen.util.propertyName
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.TypeSpec

internal val Generator<NavEntryData>.componentGetterClassName
    get() = ClassName("${data.baseName}ComponentGetter")

internal class NavEntryComponentGetterGenerator(
    override val data: NavEntryData,
) : Generator<NavEntryData>() {

    internal fun generate(): TypeSpec {
        return TypeSpec.classBuilder(componentGetterClassName)
            .addAnnotation(optInAnnotation())
            .addAnnotation(mapKeyAnnotation())
            .addAnnotation(contributesMultibindingAnnotation())
            .addSuperinterface(navEntryComponentGetter)
            .primaryConstructor(ctor())
            .addFunction(retrieveFunction())
            .build()
    }

    private fun mapKeyAnnotation(): AnnotationSpec {
        return AnnotationSpec.builder(navEntryComponentGetterKey)
            .addMember("%T::class", data.scope)
            .build()
    }

    private fun contributesMultibindingAnnotation(): AnnotationSpec {
        return AnnotationSpec.builder(ContributesMultibinding::class)
            .addMember("%T::class", data.navigation.destinationScope)
            .addMember("%T::class", navEntryComponentGetter)
            .build()
    }

    private fun ctor(): FunSpec {
        return FunSpec.constructorBuilder()
            .addAnnotation(inject)
            .build()
    }

    private fun retrieveFunction(): FunSpec {
        return FunSpec.builder("retrieve")
            .addModifiers(OVERRIDE)
            .addAnnotation(optInAnnotation(InternalCodegenApi, internalNavigatorApi))
            .addParameter("executor", navigationExecutor)
            .addParameter("context", context)
            .returns(ANY)
            .beginControlFlow(
                "return %M(%T::class, executor, context, %T::class, %T::class) " +
                    "{ parentComponent: %T, savedStateHandle, %L ->",
                getNavEntryComponent,
                data.navigation.route,
                data.parentScope,
                data.navigation.destinationScope,
                retainedParentComponentClassName,
                data.navigation.route.propertyName,
            )
            .addStatement(
                "parentComponent.%L().%L(savedStateHandle, %L)",
                retainedParentComponentGetterName,
                retainedComponentFactoryCreateName,
                data.navigation.route.propertyName,
            )
            .endControlFlow()
            .build()
    }
}