package com.dot.gallery.core.util

typealias Column = String

sealed interface Node {
    fun build(): String = when (this) {
        is Eq -> "${lhs.build()} = ${rhs.build()}"
        is Or -> "(${lhs.build()}) OR (${rhs.build()})"
        is And -> "(${lhs.build()}) AND (${rhs.build()})"
        is Like -> "${lhs.build()} LIKE ${rhs.build()}"
        is In -> "${lhs.build()} IN ${rhs.build()}"
        is Literal<*> -> "$`val`"
    }
}

private class Eq(val lhs: Node, val rhs: Node) : Node
private class Or(val lhs: Node, val rhs: Node) : Node
private class And(val lhs: Node, val rhs: Node) : Node
private class Like(val lhs: Node, val rhs: Node) : Node
private class In(val lhs: Node, val rhs: Node) : Node
private class Literal<T>(val `val`: T) : Node

class Query(val root: Node) {
    fun build() = root.build()

    companion object {
        const val ARG = "?"
    }
}

infix fun Query.or(other: Query) = Query(Or(this.root, other.root))
infix fun Query.and(other: Query) = Query(And(this.root, other.root))
infix fun Query.eq(other: Query) = Query(Eq(this.root, other.root))
infix fun <T> Column.eq(other: T) = Query(Literal(this)) eq Query(Literal(other))

/**
 * Builds a `column LIKE '<pattern>'` clause. The [pattern] is emitted as a single-quoted SQL
 * literal (not a bound `?` argument), so it MUST only ever be called with trusted, compile-time
 * constant patterns (e.g. `"%.jxl"`) to avoid SQL injection.
 */
infix fun Column.like(pattern: String) =
    Query(Like(Literal(this), Literal("'$pattern'")))

/**
 * Builds a `column IN (<v1>,<v2>,…)` clause. Values are emitted as inline literals (not bound
 * `?` arguments — query-bundle APIs cap argument count), so only numeric types are accepted;
 * they cannot carry SQL injection.
 */
infix fun Column.inValues(values: Collection<Long>) =
    Query(In(Literal(this), Literal(values.joinToString(",", "(", ")"))))

fun Iterable<Query>.join(
    func: Query.(other: Query) -> Query,
) = reduceOrNull(func)