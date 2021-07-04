package com.freeletics.mad.loona

import com.squareup.anvil.annotations.MergeComponent
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.KModifier.LATEINIT
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import org.jetbrains.kotlin.name.FqName


internal class FileGenerator(
    private val scopeClass: ClassName,
    private val loona: LoonaAnnotation,
) {

    private val retainedComponentGenerator = RetainedComponentGenerator(scopeClass, loona)
    private val viewModelGenerator = ViewModelGenerator(scopeClass, loona)
    private val rendererFragmentGenerator = RendererFragmentGenerator(scopeClass, loona)
    private val composeFragmentGenerator = ComposeFragmentGenerator(scopeClass, loona)
    private val composeGenerator = ComposeGenerator(scopeClass, loona)

    fun generate(): FileSpec {
        val builder = FileSpec.builder(scopeClass.packageName, "${scopeClass.simpleName}Loona")
            .addType(retainedComponentGenerator.generate())
            .addType(viewModelGenerator.generate())

        if (loona.extra is Extra.Compose) {
            builder.addFunction(composeGenerator.generate())
            if (loona.extra.withFragment) {
                builder.addType(composeFragmentGenerator.generate())
            }
        }

        if (loona.extra is Extra.Renderer) {
            builder.addType(rendererFragmentGenerator.generate())
        }

        return builder.build()
    }
}

internal abstract class Generator {
    abstract val scopeClass: ClassName
    abstract val loona: LoonaAnnotation

    fun ClassName(name: String): ClassName {
        return ClassName(scopeClass.packageName, name)
    }

    fun generateNavigation(): Boolean {
        return loona.navigator != null && loona.navigationHandler != null
    }
}

internal val Generator.retainedComponentClassName get() = ClassName("Retained${scopeClass.simpleName}Component")

internal const val retainedComponentFactoryCreateName = "create"

internal class RetainedComponentGenerator(
    override val scopeClass: ClassName,
    override val loona: LoonaAnnotation,
) : Generator() {

    fun generate(): TypeSpec {
        return TypeSpec.interfaceBuilder(retainedComponentClassName)
            .addAnnotation(internalApiAnnotation())
            .addAnnotation(retainedScopeAnnotation())
            .addAnnotation(componentAnnotation())
            .addProperties(componentProperties())
            .addType(retainedComponentFactory())
            .build()
    }

    private fun retainedScopeAnnotation(): AnnotationSpec {
        return AnnotationSpec.builder(scopeToRetained)
            .addMember("%T::class", scopeClass)
            .build()
    }

    private fun componentAnnotation(): AnnotationSpec {
        return AnnotationSpec.builder(MergeComponent::class)
            .addMember("scope = %T::class", scopeClass)
            .addMember("dependencies = [%T::class]", loona.dependencies)
            .build()
    }

    private fun componentProperties(): List<PropertySpec> {
        val properties = mutableListOf<PropertySpec>()
        properties += simplePropertySpec(loona.stateMachine)
        if (loona.navigator != null) {
            properties += simplePropertySpec(loona.navigator)
        }
        if (loona.navigationHandler != null) {
            properties += simplePropertySpec(loona.navigationHandler)
        }
        if (loona.extra is Extra.Renderer) {
            properties += simplePropertySpec(loona.extra.factory)
        }
        return properties
    }

    private val retainedComponentFactoryClassName = retainedComponentClassName.peerClass("Factory")

    private fun retainedComponentFactory(): TypeSpec {
        val createFun = FunSpec.builder(retainedComponentFactoryCreateName)
            .addModifiers(ABSTRACT)
            .addParameter("dependencies", loona.dependencies)
            .addParameter(bindsInstanceParameter("savedStateHandle", savedStateHandle))
            .addParameter(bindsInstanceParameter("arguments", bundle))
            .addParameter(bindsInstanceParameter("compositeDisposable", compositeDisposable))
            .addParameter(bindsInstanceParameter("coroutineScope", coroutineScope))
            .returns(retainedComponentClassName)
            .build()
        return TypeSpec.interfaceBuilder(retainedComponentFactoryClassName)
            .addAnnotation(componentFactory)
            .addFunction(createFun)
            .build()
    }
}

    /*

    View Model

     */

internal val Generator.viewModelClassName get() = ClassName("${scopeClass.simpleName}ViewModel")

internal const val viewModelComponentName = "component"

internal class ViewModelGenerator(
    override val scopeClass: ClassName,
    override val loona: LoonaAnnotation,
) : Generator() {

    internal fun generate(): TypeSpec {
        return TypeSpec.classBuilder(viewModelClassName)
            .addAnnotation(internalApiAnnotation())
            .superclass(viewModel)
            .primaryConstructor(viewModelCtor())
            .addProperties(viewModelProperties())
            .addFunction(viewModelOnClearedFun())
            .build()
    }

    private fun viewModelCtor(): FunSpec {
        return FunSpec.constructorBuilder()
            .addParameter("dependencies", loona.dependencies)
            .addParameter("savedStateHandle", savedStateHandle)
            .addParameter("arguments", bundle)
            .build()
    }

    private fun viewModelProperties(): List<PropertySpec> {
        val properties = mutableListOf<PropertySpec>()
        if (loona.rxJavaEnabled) {
            properties += PropertySpec.builder("disposable", compositeDisposable)
                .addModifiers(PRIVATE)
                .initializer("%T()", compositeDisposable)
                .build()
        }
        if (loona.coroutinesEnabled) {
            properties += PropertySpec.builder("scope", coroutineScope)
                .addModifiers(PRIVATE)
                .initializer("%M()", mainScope)
                .build()
        }
        properties += PropertySpec.builder(viewModelComponentName, retainedComponentClassName)
            .initializer(
                // Dagger prefix: external
                // factory: external
                "%T.factory().%L(dependencies, savedStateHandle, arguments, disposable, scope)",
                retainedComponentClassName.peerClass("Dagger${retainedComponentClassName.simpleName}"),
                retainedComponentFactoryCreateName
            )
            .build()
        return properties
    }

    private fun viewModelOnClearedFun(): FunSpec {
        val codeBuilder = CodeBlock.builder()
        if (loona.rxJavaEnabled) {
            codeBuilder.addStatement("disposable.clear()")
        }
        if (loona.coroutinesEnabled) {
            codeBuilder.addStatement("scope.%M()", coroutineScopeCancel)
        }
        return FunSpec.builder("onCleared")
            .addModifiers(PUBLIC, OVERRIDE)
            .addCode(codeBuilder.build())
            .build()
    }
}

internal class RendererFragmentGenerator(
    override val scopeClass: ClassName,
    override val loona: LoonaAnnotation,
) : Generator() {

    private val rendererFragmentClassName = ClassName("${scopeClass.simpleName}Fragment")

    internal fun generate(): TypeSpec {
        check(loona.extra is Extra.Renderer)

        return TypeSpec.classBuilder(rendererFragmentClassName)
            .addAnnotation(optInAnnotation())
            .superclass(fragment)
            .addProperty(lateinitPropertySpec(loona.extra.factory))
            .addProperty(lateinitPropertySpec(loona.stateMachine))
            .addFunction(rendererOnCreateViewFun())
            .addFunction(rendererInjectFun())
            .build()
    }

    private fun rendererOnCreateViewFun(): FunSpec {
        check(loona.extra is Extra.Renderer)

        return FunSpec.builder("onCreateView")
            .addModifiers(OVERRIDE)
            .addParameter("inflater", layoutInflater)
            .addParameter("container", viewGroup.copy(nullable = true))
            .addParameter("savedInstanceState", bundle.copy(nullable = true))
            .returns(view)
            .beginControlFlow("if (!::%L.isInitialized)", loona.stateMachine.propertyName)
            .addStatement("%L()", rendererFragmentInjectName)
            .endControlFlow()
            // inflate: external method
            .addStatement("val renderer = %L.inflate(inflater, container)", loona.extra.factory.propertyName)
            // connect: external method
            .addStatement("%M(renderer, %L)", rendererConnect, loona.stateMachine.propertyName)
            .addStatement("return renderer.rootView")
            .build()
    }

    private val rendererFragmentInjectName = "inject"

    private fun rendererInjectFun(): FunSpec {
        check(loona.extra is Extra.Renderer)

        return FunSpec.builder(rendererFragmentInjectName)
            .addModifiers(PRIVATE)
            .beginControlFlow("val viewModelProvider = %M<%T>(this, %T::class) { dependencies, handle -> ", viewModelProvider, loona.dependencies, loona.parentScope)
            // arguments: external method
            .addStatement("val arguments = arguments ?: %T.EMPTY", bundle)
            .addStatement("%T(dependencies, handle, arguments)", viewModelClassName)
            .endControlFlow()
            .addStatement("val viewModel = viewModelProvider[%T::class.java]", viewModelClassName)
            .addStatement("val component = viewModel.%L", viewModelComponentName)
            .addCode("\n")
            .addStatement("%1L = component.%1L", loona.extra.factory.propertyName)
            .addStatement("%1L = component.%1L", loona.stateMachine.propertyName)
            .addCode(rendererNavigationCode())
            .build()
    }

    private fun rendererNavigationCode(): CodeBlock {
        if (!generateNavigation()) {
            return CodeBlock.of("")
        }

        return CodeBlock.builder()
            .add("\n")
            .addStatement("val handler = component.%L", loona.navigationHandler!!.propertyName)
            .addStatement("val navigator = component.%L", loona.navigator!!.propertyName)
            // lifecycle: external method
            .addStatement("val scope = lifecycle.%M", lifecycleCoroutineScope)
            .addStatement("val navController = %M()", findNavController)
            .addStatement("handler.%N(scope, navController, navigator)", navigationHandlerHandle)
            .build()
    }
}

internal val Generator.composableName get() = scopeClass.simpleName

internal class ComposeGenerator(
    override val scopeClass: ClassName,
    override val loona: LoonaAnnotation,
) : Generator() {

    internal fun generate(): FunSpec {
        return FunSpec.builder(composableName)
            .addAnnotation(composable)
            .addAnnotation(optInAnnotation())
            .addParameter("navController", navController)
            .addStatement("val scope = %M()", rememberCoroutineScope)
            .addCode("\n")
            .beginControlFlow("val viewModelProvider = %M<%T>(%T::class) { dependencies, handle -> ", rememberViewModelProvider, loona.dependencies, loona.parentScope)
            // currentBackStackEntry: external method
            // arguments: external method
            .addStatement("val arguments = navController.currentBackStackEntry!!.arguments ?: %T.EMPTY", bundle)
            .addStatement("%T(dependencies, handle, arguments)", viewModelClassName)
            .endControlFlow()
            .addStatement("val viewModel = viewModelProvider[%T::class.java]", viewModelClassName)
            .addStatement("val component = viewModel.%L", viewModelComponentName)
            .addCode("\n")
            .addCode(composableNavigationSetup())
            .addStatement("val stateMachine = component.%L", loona.stateMachine.propertyName)
            .addStatement("val state = stateMachine.state.%M()", collectAsState)
            //TODO hardcoded suffix Ui for composable
            .beginControlFlow("%LUi(state.value) { action ->", composableName)
            // dispatch: external method
            .addStatement("scope.%M { stateMachine.dispatch(action) }", launch)
            .endControlFlow()
            .build()
    }

    private fun composableNavigationSetup(): CodeBlock {
        if (!generateNavigation()) {
            return CodeBlock.of("")
        }
        return CodeBlock.builder()
            .beginControlFlow("%M(scope, navController, component) {", launchedEffect)
            .addStatement("val handler = component.%L", loona.navigationHandler!!.propertyName)
            .addStatement("val navigator = component.%L", loona.navigator!!.propertyName)
            .addStatement("handler.%N(scope, navController, navigator)", navigationHandlerHandle)
            .endControlFlow()
            .add("\n")
            .build()
    }
}

internal class ComposeFragmentGenerator(
    override val scopeClass: ClassName,
    override val loona: LoonaAnnotation,
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
            .addStatement("val navController = %M()", findNavController)
            // requireContext: external method
            .addStatement("val composeView = %T(requireContext())", composeView)
            // setContent: external method
            .beginControlFlow("composeView.setContent {")
            // state: external property
            .addStatement("%L(navController)", composableName)
            .endControlFlow()
            .addStatement("return composeView")
            .build()
    }
}

// Loona Public API
private val retainedComponent = ClassName("com.freeletics.mad.loona", "RetainedComponent")
internal val retainedComponentFqName = FqName(retainedComponent.canonicalName)
private val rendererFragment = ClassName("com.freeletics.mad.loona", "RendererFragment")
internal val rendererFragmentFqName = FqName(rendererFragment.canonicalName)
private val composeFragment = ClassName("com.freeletics.mad.loona", "ComposeFragment")
internal val composeFragmentFqName = FqName(composeFragment.canonicalName)
private val compose = ClassName("com.freeletics.mad.loona", "Compose")
internal val composeFqName = FqName(compose.canonicalName)
private val scopeToRetained = ClassName("com.freeletics.mad.loona", "ScopeToRetained")

// Loona Internal API
internal val emptyNavigationHandler = ClassName("com.freeletics.mad.loona.internal", "EmptyNavigationHandler")
internal val emptyNavigator = ClassName("com.freeletics.mad.loona.internal", "EmptyNavigator")
private val internalLoonaApi = ClassName("com.freeletics.mad.loona.internal", "InternalLoonaApi")
private val viewModelProvider = MemberName("com.freeletics.mad.loona.internal", "viewModelProvider")
private val rememberViewModelProvider = MemberName("com.freeletics.mad.loona.internal", "rememberViewModelProvider")

// Navigator
private val navigationHandler = ClassName("com.freeletics.mad.navigator", "NavigationHandler")
private val navigationHandlerHandle = navigationHandler.member("handle")

// Renderer
private val rendererConnect = MemberName("com.gabrielittner.renderer.connect", "connect")

// Kotlin
private val optIn = ClassName("kotlin", "OptIn")

// Coroutines
private val coroutineScope = ClassName("kotlinx.coroutines", "CoroutineScope")
private val coroutineScopeCancel = MemberName("kotlinx.coroutines", "cancel")
private val mainScope = MemberName("kotlinx.coroutines", "MainScope")
private val launch = MemberName("kotlinx.coroutines", "launch")

// RxJava
private val compositeDisposable = ClassName("io.reactivex.disposables", "CompositeDisposable")

// Dagger
private val componentFactory = ClassName("dagger", "Component").nestedClass("Factory")
private val bindsInstance = ClassName("dagger", "BindsInstance")

// AndroidX
private val fragment = ClassName("androidx.fragment.app", "Fragment")

private val viewModel = ClassName("androidx.lifecycle", "ViewModel")
private val savedStateHandle = ClassName("androidx.lifecycle", "SavedStateHandle")
private val lifecycleCoroutineScope = MemberName("androidx.lifecycle", "coroutineScope")

private val navController = ClassName("androidx.navigation", "NavController")
private val findNavController = MemberName("androidx.navigation.fragment", "findNavController")

private val composable = ClassName("androidx.compose.runtime", "Composable")
private val launchedEffect = MemberName("androidx.compose.runtime", "LaunchedEffect")
private val collectAsState = MemberName("androidx.compose.runtime", "collectAsState")
private val getValue = MemberName("androidx.compose.runtime", "getValue")
private val rememberCoroutineScope = MemberName("androidx.compose.runtime", "rememberCoroutineScope")
private val composeView = ClassName("androidx.compose.ui.platform", "ComposeView")

// Android
private val layoutInflater = ClassName("android.view", "LayoutInflater")
private val viewGroup = ClassName("android.view", "ViewGroup")
private val view = ClassName("android.view", "View")
private val bundle = ClassName("android.os", "Bundle")

private val ClassName.propertyName: String get() {
    return simpleNames.first().replaceFirstChar(Char::lowercaseChar) +
            simpleNames.drop(1).joinToString { it.replaceFirstChar(Char::uppercaseChar) }
}

private fun bindsInstanceParameter(name: String, className: ClassName): ParameterSpec {
    return ParameterSpec.builder(name, className)
        .addAnnotation(bindsInstance)
        .build()
}

private fun simplePropertySpec(className: ClassName): PropertySpec {
    return PropertySpec.builder(className.propertyName, className).build()
}

private fun lateinitPropertySpec(className: ClassName): PropertySpec {
    return PropertySpec.builder(className.propertyName, className)
        .addModifiers(PRIVATE, LATEINIT)
        .mutable()
        .build()
}

private fun internalApiAnnotation(): AnnotationSpec {
    return AnnotationSpec.builder(internalLoonaApi).build()
}

private fun optInAnnotation(): AnnotationSpec {
    return AnnotationSpec.builder(optIn)
        .addMember("%T::class", internalLoonaApi)
        .build()
}
