package com.freeletics.mad.screens.codegen

import com.freeletics.mad.screens.ScreenData
import com.squareup.kotlinpoet.ClassName

internal abstract class Generator {
    abstract val scopeClass: ClassName
    abstract val data: ScreenData

    fun ClassName(name: String): ClassName {
        return ClassName(scopeClass.packageName, name)
    }
}
