package com.freeletics.mad.screens

import com.squareup.kotlinpoet.ClassName

data class ScreenData(
    val parentScope: ClassName,
    val dependencies: ClassName,

    val stateMachine: ClassName,

    val navigator: ClassName?,
    val navigationHandler: ClassName?,

    val coroutinesEnabled: Boolean,
    val rxJavaEnabled: Boolean,

    val extra: Extra?
)

sealed class Extra {
    data class Compose(val withFragment: Boolean) : Extra()
    data class Renderer(val factory: ClassName) : Extra()
}
