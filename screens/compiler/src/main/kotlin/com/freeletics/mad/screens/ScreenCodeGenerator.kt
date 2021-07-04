package com.freeletics.mad.screens

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
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

@OptIn(ExperimentalAnvilApi::class)
@AutoService(CodeGenerator::class)
class ScreenCodeGenerator : CodeGenerator {

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
        var screens = component.toScreenData(module)

        val compose = clazz.findAnnotation(composeFqName, module)
        if (compose != null) {
            screens = screens.copy(extra = Extra.Compose(withFragment = false))
        }

        val composeFragment = clazz.findAnnotation(composeFragmentFqName, module)
        if (composeFragment != null) {
            screens = screens.copy(extra = Extra.Compose(withFragment = true))
        }

        val renderer = clazz.findAnnotation(rendererFragmentFqName, module)
        if (renderer != null) {
            val factory = renderer.requireClassArgument("rendererFactory", 0, module)
            screens = screens.copy(extra = Extra.Renderer(factory))
        }

        val scopeClass = clazz.asClassName()
        val file = FileGenerator(scopeClass, screens).generate()
        return createGeneratedFile(
            codeGenDir = codeGenDir,
            packageName = file.packageName,
            fileName = file.name,
            content = file.toString()
        )
    }

    private fun KtAnnotationEntry.toScreenData(
        module: ModuleDescriptor
    ) = ScreenData(
        parentScope = requireClassArgument("parentScope", 0, module),
        dependencies = requireClassArgument("dependencies", 1, module),
        stateMachine = requireClassArgument("stateMachine", 2, module),
        navigator = requireClassArgument("navigator", 3, module),
        navigationHandler = requireClassArgument("navigationHandler", 4, module),
        coroutinesEnabled = requireBooleanArgument("coroutinesEnabled", 5, module),
        rxJavaEnabled = requireBooleanArgument("rxJavaEnabled", 6, module),
        extra = null
    ).clearEmptyNavigation(this)

    private fun ScreenData.clearEmptyNavigation(element: PsiElement): ScreenData {
        // both parameters were set -> generate navigator
        if (navigator != emptyNavigator && navigationHandler != emptyNavigationHandler) {
            return this
        }

        if (navigator == emptyNavigator) {
            throw AnvilCompilationException(
                "navigator needs to be set if navigationHandler was set",
                element = element
            )
        }
        if (navigationHandler == emptyNavigationHandler) {
            throw AnvilCompilationException(
                "navigationHandler needs to be set if navigator was set",
                element = element
            )
        }

        return copy(navigator = null, navigationHandler = null)
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
            "Couldn't find parentScope for ${requireFqName(module)}",
            element = this
        )
    }

    private fun KtAnnotationEntry.requireBooleanArgument(
        name: String,
        index: Int,
        module: ModuleDescriptor
    ): Boolean {
        val boolean = findAnnotationArgument<Boolean>(name, index)
        if (boolean != null) {
            return boolean
        }
        throw AnvilCompilationException(
            "Couldn't find parentScope for ${requireFqName(module)}",
            element = this
        )
    }
}
