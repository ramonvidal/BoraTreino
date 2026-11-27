package com.boratreino.core.testing

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * A stand-in for the interface [T] built by reflection: [answers] maps a member name to the value it returns
 * (usually a flow). Any other call fails the test with [NotStubbedException], so each test states which calls it
 * depends on. Prefer the `Stub...` classes; use this where a test wants that strictness.
 *
 * - A Kotlin property is answered by its name: `"weeklyGoal"` answers `getWeeklyGoal()`, `"isOn"` answers `isOn()`.
 * - A function is answered by its name whatever its arguments; overloads share the answer.
 * - A suspend function returns its answer without suspending.
 */
inline fun <reified T : Any> answering(vararg answers: Pair<String, Any?>): T = answering(T::class.java, answers.toMap())

/** [answering] for a class object, for callers that are not inline. */
fun <T : Any> answering(type: Class<T>, answers: Map<String, Any?>): T {
    require(type.isInterface) { "answering() needs an interface, not ${type.name}" }
    val handler = InvocationHandler { proxy, method, args ->
        when {
            method.isObjectMethod("toString") -> "answering ${type.simpleName}"
            method.isObjectMethod("hashCode") -> System.identityHashCode(proxy)
            method.isObjectMethod("equals") -> proxy === args?.firstOrNull()
            else -> {
                val name = answerName(method.name, answers.keys)
                    ?: throw NotStubbedException("${type.simpleName}.${method.name} (answering)")
                answers[name]
            }
        }
    }
    return type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler))
}

private fun Method.isObjectMethod(name: String) = this.name == name && declaringClass == Any::class.java

/** The key that answers [method]: its own name, or the property name behind a `getX` / `isX` getter. */
private fun answerName(method: String, keys: Set<String>): String? {
    if (method in keys) return method
    if (method.startsWith("get") && method.length > 3) {
        val property = method.removePrefix("get").replaceFirstChar { it.lowercase() }
        if (property in keys) return property
    }
    // `val isOn: Boolean` compiles to `isOn()`, already matched by name above.
    return null
}
