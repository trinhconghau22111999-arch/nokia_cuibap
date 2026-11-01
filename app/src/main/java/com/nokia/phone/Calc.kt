package com.nokia.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

private const val OPS = "+−×÷"

class CalcState {
    var expr by mutableStateOf("")
    var result by mutableStateOf("")
    var done by mutableStateOf(false)

    private fun lastNumber(): String = expr.substring(expr.indexOfLast { it in OPS } + 1)

    private fun fresh() { if (done) { expr = ""; result = ""; done = false } }

    fun digit(d: String) {
        fresh()
        if (lastNumber() == "0") expr = expr.dropLast(1) + d else expr += d
    }

    fun comma() {
        fresh()
        val ln = lastNumber()
        if (',' in ln) return
        expr += if (ln.isEmpty()) "0," else ","
    }

    fun op(o: Char) {
        if (done) {
            if (result.isEmpty() || result == "Lỗi") return
            expr = result; result = ""; done = false
        }
        if (expr.isEmpty()) { if (o == '−') expr = "−"; return }
        val last = expr.last()
        when {
            last in OPS -> if (expr.length > 1) expr = expr.dropLast(1) + o
            last == ',' -> expr = expr.dropLast(1) + o
            else -> expr += o
        }
    }

    fun back() {
        if (done) { clear(); return }
        if (expr.isNotEmpty()) expr = expr.dropLast(1)
    }

    fun clear() { expr = ""; result = ""; done = false }

    fun compute() {
        if (expr.isEmpty()) return
        result = CalcEval.eval(expr) ?: "Lỗi"
        done = true
    }

    /** Kết quả tạm hiện ngay khi đang gõ phép tính. */
    val preview: String
        get() = if (!done && expr.drop(1).any { it in OPS }) (CalcEval.eval(expr) ?: "") else ""

    fun tap(t: String) {
        when (t) {
            "C" -> clear()
            "←" -> back()
            "," -> comma()
            "=" -> compute()
            "+", "−", "×", "÷" -> op(t[0])
            else -> digit(t)
        }
    }
}

object CalcEval {
    fun eval(src: String): String? {
        var s = src
        while (s.isNotEmpty() && (s.last() in OPS || s.last() == ',')) s = s.dropLast(1)
        if (s.isEmpty()) return null
        var i = 0
        val n = s.length
        var neg = false
        if (s[0] == '−') { neg = true; i = 1 }
        val nums = ArrayList<BigDecimal>()
        val ops = ArrayList<Char>()
        while (i < n) {
            val st = i
            while (i < n && (s[i].isDigit() || s[i] == ',')) i++
            if (st == i) return null
            var v = try { BigDecimal(s.substring(st, i).replace(',', '.')) } catch (_: Exception) { return null }
            if (nums.isEmpty() && neg) v = v.negate()
            nums.add(v)
            if (i < n) { ops.add(s[i]); i++ }
        }
        if (nums.isEmpty() || ops.size != nums.size - 1) return null
        // nhân chia trước
        val vals = ArrayList<BigDecimal>()
        val adds = ArrayList<Char>()
        vals.add(nums[0])
        for (k in ops.indices) {
            val b = nums[k + 1]
            when (ops[k]) {
                '×' -> vals[vals.size - 1] = vals[vals.size - 1].multiply(b)
                '÷' -> {
                    if (b.signum() == 0) return null
                    vals[vals.size - 1] = vals[vals.size - 1].divide(b, MathContext.DECIMAL64)
                }
                else -> { adds.add(ops[k]); vals.add(b) }
            }
        }
        var acc = vals[0]
        for (k in adds.indices) acc = if (adds[k] == '+') acc.add(vals[k + 1]) else acc.subtract(vals[k + 1])
        return fmt(acc)
    }

    private fun fmt(v: BigDecimal): String {
        var d = v
        if (d.scale() > 10) d = d.setScale(10, RoundingMode.HALF_UP)
        var t = if (d.signum() == 0) "0" else d.stripTrailingZeros().toPlainString()
        if (t.length > 18) t = v.round(MathContext(10)).toString()
        return t.replace('.', ',').replace('-', '−')
    }
}

@Composable
fun CalcScreen(c: CalcState) {
    val shown = c.expr.ifEmpty { "0" }
    val big = when {
        shown.length <= 8 -> 38
        shown.length <= 10 -> 33
        shown.length <= 13 -> 26
        shown.length <= 18 -> 19
        else -> 14
    }
    val second = if (c.done) "= " + c.result else c.preview.let { if (it.isEmpty()) "" else "= $it" }
    Column(Modifier.fillMaxSize()) {
        // Ô hiển thị phép tính lớn ở phía trên
        Column(
            Modifier.fillMaxWidth().weight(0.28f).border(2.dp, INK).padding(horizontal = 6.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.End
        ) {
            Text(shown, color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold,
                fontSize = big.sp, maxLines = 2, textAlign = TextAlign.End)
            Text(second, color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold,
                fontSize = if (c.done) 20.sp else 18.sp, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth().weight(0.72f)) {
            CalcRow(listOf("C", "←", "÷", "×"), c)
            CalcRow(listOf("7", "8", "9", "−"), c)
            CalcRow(listOf("4", "5", "6", "+"), c)
            Row(Modifier.weight(2f).fillMaxWidth()) {
                Column(Modifier.weight(3f).fillMaxHeight()) {
                    CalcRow(listOf("1", "2", "3"), c)
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        CalcBtn("0", Modifier.weight(2f).fillMaxHeight(), c)
                        CalcBtn(",", Modifier.weight(1f).fillMaxHeight(), c)
                    }
                }
                CalcBtn("=", Modifier.weight(1f).fillMaxHeight(), c)
            }
        }
    }
}

@Composable
fun ColumnScope.CalcRow(items: List<String>, c: CalcState) {
    Row(Modifier.weight(1f).fillMaxWidth()) {
        items.forEach { CalcBtn(it, Modifier.weight(1f).fillMaxHeight(), c) }
    }
}

@Composable
fun CalcBtn(t: String, mod: Modifier, c: CalcState) {
    val op = t == "÷" || t == "×" || t == "−" || t == "+" || t == "="
    Column(
        mod.padding(2.dp).background(if (op) INK else LCD).border(2.dp, INK).clickable { c.tap(t) },
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(t, color = if (op) LCD else INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 24.sp)
    }
}
