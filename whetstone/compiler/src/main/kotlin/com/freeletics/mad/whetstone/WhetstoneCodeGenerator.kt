package com.freeletics.mad.whetstone

import com.freeletics.mad.whetstone.codegen.FileGenerator
import com.freeletics.mad.whetstone.codegen.composeFqName
import com.freeletics.mad.whetstone.codegen.composeFragmentFqName
import com.freeletics.mad.whetstone.codegen.emptyNavigationHandler
import com.freeletics.mad.whetstone.codegen.emptyNavigator
import com.freeletics.mad.whetstone.codegen.rendererFragmentFqName
import com.freeletics.mad.whetstone.codegen.retainedComponentFqName
import com.google.auto.service.AutoService
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.classesAndInnerClass
import com.squareup.anvil.compiler.internal.findAnnotation
import com.squareup.anvil.compiler.internal.findAnnotationArgument
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.kotlinpoet.ClassName
import java.io.File
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtFile

@OptIn(ExperimentalAnvilApi::class)
@AutoService(CodeGenerator::class)
class WhetstoneCodeGenerator : CodeGenerator {

    override fun isApplicable(context: AnvilContext): Boolean = !context.disableComponentMerging

    override fun generateCode(
        codeGenDir: File,
        module: ModuleDescriptor,
        projectFiles: Collection<KtFile>
    ): Collection<GeneratedFile> {
        return projectFiles
            .classesAndInnerClass(module)
            .mapNotNull { clazz -> generateCode(codeGenDir, module, clazz) }
            .toList()
    }

    private fun generateCode(
        codeGenDir: File,
        module: ModuleDescriptor,
        clazz: KtClassOrObject
    ): GeneratedFile? {
        val component = clazz.findAnnotation(retainedComponentFqName, module) ?: return null
        var whetstone = component.toScreenData(module)

        val compose = clazz.findAnnotation(composeFqName, module)
        if (compose != null) {
            whetstone = whetstone.copy(extra = Extra.Compose(withFragment = false))
        }

        val composeFragment = clazz.findAnnotation(composeFragmentFqName, module)
        if (composeFragment != null) {
            whetstone = whetstone.copy(extra = Extra.Compose(withFragment = true))
        }

        val renderer = clazz.findAnnotation(rendererFragmentFqName, module)
        if (renderer != null) {
            val factory = renderer.requireClassArgument("rendererFactory", 0, module)
            whetstone = whetstone.copy(extra = Extra.Renderer(factory))
        }

        val scopeClass = clazz.asClassName()
        val file = FileGenerator(scopeClass, whetstone).generate()
        return createGeneratedFile(
            codeGenDir = codeGenDir,
            packageName = file.packageName,
            fileName = file.name,
            content = file.toString()
        )
    }

    private fun KtAnnotationEntry.toScreenData(
        module: ModuleDescriptor
    ): Data {
        return Data(
            parentScope = requireClassArgument("parentScope", 0, module),
            dependencies = requireClassArgument("dependencies", 1, module),
            stateMachine = requireClassArgument("stateMachine", 2, module),
            navigation = toNavigation(module),
            coroutinesEnabled = optionalBooleanArgument("coroutinesEnabled", 5, module) ?: false,
            rxJavaEnabled = optionalBooleanArgument("rxJavaEnabled", 6, module) ?: false,
            extra = null
        )
    }

    private fun KtAnnotationEntry.toNavigation(
        module: ModuleDescriptor
    ): Navigation? {
        val navigator = optionalClassArgument("navigator", 3, module)
        val navigationHandler = optionalClassArgument("navigationHandler", 4, module)

        if (navigator != null && navigationHandler != null &&
            navigator != emptyNavigator && navigationHandler != emptyNavigationHandler) {
            return Navigation(navigator, navigationHandler)
        }
        if (navigator == null && navigationHandler == null) {
            return null
        }
        if (navigator == emptyNavigator && navigationHandler == emptyNavigationHandler) {
            return null
        }

        throw IllegalStateException("navigator and navigationHandler need to be set together")
    }

    private fun KtAnnotationEntry.requireClassArgument(
        name: String,
        index: Int,
        module: ModuleDescriptor
    ): ClassName {
        val classLiteralExpression = findAnnotationArgument<KtClassLiteralExpression>(name, index)
        if (classLiteralExpression != null) {
            return classLiteralExpression.requireFqName(module).asClassName(module)
        }
        throw AnvilCompilationException(
            "Couldn't find $name for ${requireFqName(module)}",
            element = this
        )
    }

    //TODO replace with a way to get default value
    private fun KtAnnotationEntry.optionalClassArgument(
        name: String,
        index: Int,
        module: ModuleDescriptor
    ): ClassName? {
        val classLiteralExpression = findAnnotationArgument<KtClassLiteralExpression>(name, index)
        if (classLiteralExpression != null) {
            return classLiteralExpression.requireFqName(module).asClassName(module)
        }
        return null
    }

    private fun KtAnnotationEntry.requireBooleanArgument(
        name: String,
        index: Int,
        module: ModuleDescriptor
    ): Boolean {
        val boolean = findAnnotationArgument<KtConstantExpression>(name, index)
        if (boolean != null) {
            return boolean.node.firstChildNode.text.toBoolean()
        }
        throw AnvilCompilationException(
            "Couldn't find $name for ${requireFqName(module)}",
            element = this
        )
    }

    //TODO replace with a way to get default value
    private fun KtAnnotationEntry.optionalBooleanArgument(
        name: String,
        index: Int,
        module: ModuleDescriptor
    ): Boolean? {
        val boolean = findAnnotationArgument<KtConstantExpression>(name, index)
        if (boolean != null) {
            return boolean.node.firstChildNode.text.toBoolean()
        }
        return null
    }
}
