package com.boratreino.core.testing

/**
 * Thrown by a stub member that has no neutral answer (for example a write that must return the saved set).
 * Override the member in the test that needs it.
 */
class NotStubbedException(member: String) : IllegalStateException("$member is not stubbed: override it in this test")

internal fun Any.notStubbed(member: String): Nothing = throw NotStubbedException("${javaClass.simpleName}.$member")
