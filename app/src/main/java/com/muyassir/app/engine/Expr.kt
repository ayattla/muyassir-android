package com.muyassir.app.engine

import kotlin.math.*

/** Recursive-descent evaluator for the scientific calculator. Supports + − × ÷ ^ % ! ( ) and common functions. */
class Expr(private val src: String, private val degrees: Boolean = true) {
    private var pos = 0
    private val s = src.replace('×', '*').replace('÷', '/').replace('−', '-').replace("π", "pi").replace("√", "sqrt").replace(" ", "")

    fun eval(): Double {
        val v = expr()
        if (pos < s.length) error("رمز غير متوقع: ${s[pos]}")
        return v
    }

    private fun peek() = if (pos < s.length) s[pos] else '\u0000'
    private fun eat(c: Char): Boolean = if (peek() == c) { pos++; true } else false

    private fun expr(): Double {
        var v = term()
        while (true) v = when {
            eat('+') -> v + term()
            eat('-') -> v - term()
            else -> return v
        }
    }

    private fun term(): Double {
        var v = factor()
        while (true) v = when {
            eat('*') -> v * factor()
            eat('/') -> v / factor()
            else -> return v
        }
    }

    private fun factor(): Double = unary()

    private fun unary(): Double = when {
        eat('-') -> -unary()
        eat('+') -> unary()
        else -> power()
    }

    /** Right-associative power; the exponent may carry its own sign (2^-1). */
    private fun power(): Double {
        val b = postfix(primary())
        return if (eat('^')) b.pow(unary()) else b
    }

    private fun postfix(v0: Double): Double {
        var v = v0
        while (true) v = when {
            eat('!') -> fact(v)
            eat('%') -> v / 100
            else -> return v
        }
    }

    private fun fact(v: Double): Double {
        if (v < 0 || v != floor(v) || v > 170) error("المضروب لأعداد صحيحة من 0 إلى 170")
        var r = 1.0; for (i in 2..v.toInt()) r *= i; return r
    }

    private fun primary(): Double {
        if (eat('(')) { val v = expr(); if (!eat(')')) error("قوس ناقص"); return v }
        val start = pos
        if (peek().isDigit() || peek() == '.') {
            while (peek().isDigit() || peek() == '.') pos++
            if (peek() == 'e' || peek() == 'E') { val save = pos; pos++; if (peek() == '-' || peek() == '+') pos++; if (peek().isDigit()) { while (peek().isDigit()) pos++ } else pos = save }
            return s.substring(start, pos).toDouble()
        }
        while (peek().isLetter()) pos++
        val name = s.substring(start, pos).lowercase()
        if (name.isEmpty()) error("تعبير غير مكتمل")
        when (name) { "pi" -> return PI; "e" -> return E }
        val arg = primary()
        val r = if (degrees) Math.toRadians(arg) else arg
        return when (name) {
            "sin" -> sin(r); "cos" -> cos(r); "tan" -> tan(r)
            "asin" -> asin(arg).let { if (degrees) Math.toDegrees(it) else it }
            "acos" -> acos(arg).let { if (degrees) Math.toDegrees(it) else it }
            "atan" -> atan(arg).let { if (degrees) Math.toDegrees(it) else it }
            "sqrt", "√" -> sqrt(arg); "log" -> log10(arg); "ln" -> ln(arg); "abs" -> abs(arg)
            "exp" -> exp(arg)
            else -> error("دالة غير معروفة: $name")
        }
    }
}
