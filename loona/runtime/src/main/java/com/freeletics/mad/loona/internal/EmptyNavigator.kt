package com.freeletics.mad.loona.internal

import com.freeletics.mad.loona.Compose
import com.freeletics.mad.loona.RendererFragment
import com.freeletics.mad.navigator.Navigator
import com.freeletics.mad.navigator.NavigationHandler

/**
 * Default value for the `navigagor` parameter of [Compose] and [RendererFragment]. When the
 * generator finds this class as value it will skip generating navigation related code.
 * This allows consumers to not use [Navigator] and [NavigationHandler] based implementations and
 * just have the standard state machine and ui setup in the generated Fragment.
 */
internal class EmptyNavigator : Navigator {
    init {
        throw UnsupportedOperationException("This is a marker class that should never be used")
    }
}
