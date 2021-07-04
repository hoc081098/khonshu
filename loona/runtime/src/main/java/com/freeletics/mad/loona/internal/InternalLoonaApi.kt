package com.freeletics.mad.loona.internal

/**
 * Marks runtime APIs as well as generated code that should only be used by other generated code.
 * Code marked with [InternalLoonaApi] has no guarantees about API stability and can be changed
 * at any time.
 */
@RequiresOptIn
annotation class InternalLoonaApi
