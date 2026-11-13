package com.fuzzydl.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ícones de traço fino, originais e desenhados no Canvas (sem Material Icons). */
enum class AppIcon {
    Download, Grid, Sliders, Bell, ChevronRight, Play, Pause, Close, Refresh,
    Link, Wifi, Gauge, Moon, Storage, Folder, Code, Info, Sparkle, Check,
    Alert, Clock, Database, Plus, Minus, Store, Library, ExternalLink, ChevronLeft,
    Search, List, Trash, Star, Filter,
}

@Composable
fun AppIconView(
    icon: AppIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    strokeWidth: Dp = 1.8.dp,
) {
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val ox = (this.size.width - 24f * unit) / 2f
        val oy = (this.size.height - 24f * unit) / 2f
        fun p(x: Float, y: Float) = Offset(ox + x * unit, oy + y * unit)
        val stroke = Stroke(
            width = strokeWidth.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val w = strokeWidth.toPx()

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, p(x1, y1), p(x2, y2), strokeWidth = w, cap = StrokeCap.Round)

        fun poly(vararg points: Float) {
            val path = Path().apply {
                moveTo(p(points[0], points[1]).x, p(points[0], points[1]).y)
                var i = 2
                while (i < points.size) {
                    val o = p(points[i], points[i + 1])
                    lineTo(o.x, o.y)
                    i += 2
                }
            }
            drawPath(path, tint, style = stroke)
        }

        fun circle(cx: Float, cy: Float, r: Float, filled: Boolean = false) {
            drawCircle(
                color = tint,
                radius = r * unit,
                center = p(cx, cy),
                style = if (filled) Fill else stroke,
            )
        }

        when (icon) {
            AppIcon.Download -> {
                line(12f, 3f, 12f, 15f)
                poly(7f, 10f, 12f, 15f, 17f, 10f)
                line(4f, 20f, 20f, 20f)
            }
            AppIcon.Grid -> {
                val c = CornerRadius(2.2f * unit)
                listOf(
                    4f to 4f, 13f to 4f, 4f to 13f, 13f to 13f,
                ).forEach { (x, y) ->
                    drawRoundRect(
                        color = tint,
                        topLeft = p(x, y),
                        size = Size(7f * unit, 7f * unit),
                        cornerRadius = c,
                        style = stroke,
                    )
                }
            }
            AppIcon.Sliders -> {
                line(4f, 6f, 20f, 6f)
                line(4f, 12f, 20f, 12f)
                line(4f, 18f, 20f, 18f)
                listOf(9f to 6f, 15f to 12f, 7f to 18f).forEach { (x, y) ->
                    circle(x, y, 2.1f, filled = true)
                }
            }
            AppIcon.Bell -> {
                val path = Path().apply {
                    val a = p(6f, 17f); moveTo(a.x, a.y)
                    val b = p(18f, 17f); lineTo(b.x, b.y)
                    val c = p(16.6f, 15.2f); lineTo(c.x, c.y)
                    val d = p(16.6f, 10f); lineTo(d.x, d.y)
                    val e = p(12f, 5f); lineTo(e.x, e.y)
                    val f = p(7.4f, 10f); lineTo(f.x, f.y)
                    val g = p(7.4f, 15.2f); lineTo(g.x, g.y)
                    close()
                }
                drawPath(path, tint, style = stroke)
                poly(10f, 20f, 12f, 21f, 14f, 20f)
            }
            AppIcon.ChevronRight -> poly(9f, 5f, 16f, 12f, 9f, 19f)
            AppIcon.ChevronLeft -> poly(15f, 5f, 8f, 12f, 15f, 19f)
            AppIcon.Play -> {
                val path = Path().apply {
                    val a = p(8f, 5f); moveTo(a.x, a.y)
                    val b = p(19f, 12f); lineTo(b.x, b.y)
                    val c = p(8f, 19f); lineTo(c.x, c.y)
                    close()
                }
                drawPath(path, tint, style = Fill)
            }
            AppIcon.Pause -> {
                val r = CornerRadius(1.2f * unit)
                drawRoundRect(tint, p(7f, 5f), Size(3.2f * unit, 14f * unit), r)
                drawRoundRect(tint, p(13.8f, 5f), Size(3.2f * unit, 14f * unit), r)
            }
            AppIcon.Close -> {
                line(6f, 6f, 18f, 18f)
                line(18f, 6f, 6f, 18f)
            }
            AppIcon.Refresh -> {
                drawArc(
                    color = tint,
                    startAngle = -55f,
                    sweepAngle = 290f,
                    useCenter = false,
                    topLeft = p(4f, 4f),
                    size = Size(16f * unit, 16f * unit),
                    style = stroke,
                )
                poly(16.7f, 3.2f, 19.2f, 7.2f, 14.6f, 7.8f)
            }
            AppIcon.Link -> {
                line(9.2f, 14.8f, 14.8f, 9.2f)
                drawArc(
                    color = tint, startAngle = 120f, sweepAngle = 240f, useCenter = false,
                    topLeft = p(3.5f, 8f), size = Size(9f * unit, 9f * unit), style = stroke,
                )
                drawArc(
                    color = tint, startAngle = -60f, sweepAngle = 240f, useCenter = false,
                    topLeft = p(11.5f, 7f), size = Size(9f * unit, 9f * unit), style = stroke,
                )
            }
            AppIcon.Wifi -> {
                drawArc(
                    color = tint, startAngle = 210f, sweepAngle = 120f, useCenter = false,
                    topLeft = p(2f, 3f), size = Size(20f * unit, 20f * unit), style = stroke,
                )
                drawArc(
                    color = tint, startAngle = 210f, sweepAngle = 120f, useCenter = false,
                    topLeft = p(6f, 8f), size = Size(12f * unit, 12f * unit), style = stroke,
                )
                circle(12f, 18.6f, 1.4f, filled = true)
            }
            AppIcon.Gauge -> {
                drawArc(
                    color = tint, startAngle = 150f, sweepAngle = 240f, useCenter = false,
                    topLeft = p(3.5f, 3.5f), size = Size(17f * unit, 17f * unit), style = stroke,
                )
                line(12f, 13f, 16.4f, 8.6f)
                circle(12f, 13f, 1.6f, filled = true)
            }
            AppIcon.Moon -> {
                val path = Path().apply {
                    val a = p(20f, 14.6f)
                    moveTo(a.x, a.y)
                    cubicTo(
                        p(18.2f, 18.8f).x, p(18.2f, 18.8f).y,
                        p(12.6f, 20.4f).x, p(12.6f, 20.4f).y,
                        p(8.3f, 18.4f).x, p(8.3f, 18.4f).y,
                    )
                    cubicTo(
                        p(3.4f, 15.8f).x, p(3.4f, 15.8f).y,
                        p(3.2f, 8.4f).x, p(3.2f, 8.4f).y,
                        p(8.2f, 4.6f).x, p(8.2f, 4.6f).y,
                    )
                    cubicTo(
                        p(6.2f, 10f).x, p(6.2f, 10f).y,
                        p(9.4f, 18.2f).x, p(9.4f, 18.2f).y,
                        p(20f, 14.6f).x, p(20f, 14.6f).y,
                    )
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            AppIcon.Storage -> {
                drawRoundRect(
                    color = tint,
                    topLeft = p(3f, 6.5f),
                    size = Size(18f * unit, 11f * unit),
                    cornerRadius = CornerRadius(3f * unit),
                    style = stroke,
                )
                line(7f, 12f, 13f, 12f)
                circle(17f, 12f, 1.1f, filled = true)
            }
            AppIcon.Folder -> {
                val path = Path().apply {
                    val a = p(3.5f, 7f); moveTo(a.x, a.y)
                    val b = p(9f, 7f); lineTo(b.x, b.y)
                    val c = p(11f, 9.4f); lineTo(c.x, c.y)
                    val d = p(20.5f, 9.4f); lineTo(d.x, d.y)
                    val e = p(20.5f, 19f); lineTo(e.x, e.y)
                    val f = p(3.5f, 19f); lineTo(f.x, f.y)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            AppIcon.Code -> {
                poly(8.5f, 7f, 3.5f, 12f, 8.5f, 17f)
                poly(15.5f, 7f, 20.5f, 12f, 15.5f, 17f)
                line(13.4f, 5f, 10.6f, 19f)
            }
            AppIcon.Info -> {
                circle(12f, 12f, 9f)
                line(12f, 11f, 12f, 17f)
                circle(12f, 7.2f, 1f, filled = true)
            }
            AppIcon.Sparkle -> {
                val path = Path().apply {
                    val a = p(12f, 3f); moveTo(a.x, a.y)
                    val b = p(14f, 10f); lineTo(b.x, b.y)
                    val c = p(21f, 12f); lineTo(c.x, c.y)
                    val d = p(14f, 14f); lineTo(d.x, d.y)
                    val e = p(12f, 21f); lineTo(e.x, e.y)
                    val f = p(10f, 14f); lineTo(f.x, f.y)
                    val g = p(3f, 12f); lineTo(g.x, g.y)
                    val h = p(10f, 10f); lineTo(h.x, h.y)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            AppIcon.Check -> poly(4.5f, 12.5f, 9.5f, 17.5f, 19.5f, 6.5f)
            AppIcon.Alert -> {
                val path = Path().apply {
                    val a = p(12f, 3.5f); moveTo(a.x, a.y)
                    val b = p(21f, 19.5f); lineTo(b.x, b.y)
                    val c = p(3f, 19.5f); lineTo(c.x, c.y)
                    close()
                }
                drawPath(path, tint, style = stroke)
                line(12f, 9f, 12f, 13.5f)
                circle(12f, 16.5f, 1f, filled = true)
            }
            AppIcon.Clock -> {
                circle(12f, 12f, 9f)
                line(12f, 7f, 12f, 12.4f)
                line(12f, 12.4f, 15.8f, 14.6f)
            }
            AppIcon.Database -> {
                drawArc(
                    color = tint, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                    topLeft = p(4f, 4f), size = Size(16f * unit, 6f * unit), style = stroke,
                )
                line(4f, 7f, 4f, 17f)
                line(20f, 7f, 20f, 17f)
                drawArc(
                    color = tint, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                    topLeft = p(4f, 14f), size = Size(16f * unit, 6f * unit), style = stroke,
                )
                drawArc(
                    color = tint, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                    topLeft = p(4f, 9f), size = Size(16f * unit, 6f * unit), style = stroke,
                )
            }
            AppIcon.Plus -> {
                line(12f, 5f, 12f, 19f)
                line(5f, 12f, 19f, 12f)
            }
            AppIcon.Minus -> line(5f, 12f, 19f, 12f)
            AppIcon.Store -> {
                val path = Path().apply {
                    val a = p(5f, 8f); moveTo(a.x, a.y)
                    val b = p(19f, 8f); lineTo(b.x, b.y)
                    val c = p(17.6f, 20f); lineTo(c.x, c.y)
                    val d = p(6.4f, 20f); lineTo(d.x, d.y)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawArc(
                    color = tint, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = p(8f, 3f), size = Size(8f * unit, 9f * unit), style = stroke,
                )
            }
            AppIcon.Library -> {
                val r = CornerRadius(1.4f * unit)
                drawRoundRect(tint, p(4f, 4f), Size(4.6f * unit, 16f * unit), r, style = stroke)
                drawRoundRect(tint, p(10f, 4f), Size(4.6f * unit, 16f * unit), r, style = stroke)
                val lean = Path().apply {
                    val a = p(16.4f, 5f); moveTo(a.x, a.y)
                    val b = p(20.4f, 6.2f); lineTo(b.x, b.y)
                    val c = p(17.6f, 19.6f); lineTo(c.x, c.y)
                    val d = p(13.6f, 18.4f); lineTo(d.x, d.y)
                    close()
                }
                drawPath(lean, tint, style = stroke)
            }
            AppIcon.Search -> {
                circle(11f, 11f, 6.5f)
                line(15.8f, 15.8f, 20.5f, 20.5f)
            }
            AppIcon.List -> {
                line(4f, 7f, 5f, 7f)
                line(8f, 7f, 20f, 7f)
                line(4f, 12f, 5f, 12f)
                line(8f, 12f, 20f, 12f)
                line(4f, 17f, 5f, 17f)
                line(8f, 17f, 20f, 17f)
            }
            AppIcon.Trash -> {
                line(4f, 6.5f, 20f, 6.5f)
                line(9.5f, 6.5f, 10f, 4f)
                line(14.5f, 6.5f, 14f, 4f)
                val path = Path().apply {
                    val a = p(6f, 7f); moveTo(a.x, a.y)
                    val b = p(7.4f, 20f); lineTo(b.x, b.y)
                    val c = p(16.6f, 20f); lineTo(c.x, c.y)
                    val d = p(18f, 7f); lineTo(d.x, d.y)
                }
                drawPath(path, tint, style = stroke)
                line(10.5f, 10.5f, 10.5f, 17f)
                line(13.5f, 10.5f, 13.5f, 17f)
            }
            AppIcon.Star -> {
                val path = Path().apply {
                    val pts = listOf(12f to 3.5f, 14.6f to 9.2f, 20.8f to 9.9f, 16.2f to 14.1f, 17.5f to 20.2f, 12f to 17.1f, 6.5f to 20.2f, 7.8f to 14.1f, 3.2f to 9.9f, 9.4f to 9.2f)
                    val first = p(pts[0].first, pts[0].second); moveTo(first.x, first.y)
                    pts.drop(1).forEach { (x, y) -> val o = p(x, y); lineTo(o.x, o.y) }
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            AppIcon.Filter -> {
                line(4f, 6f, 20f, 6f)
                line(7f, 12f, 17f, 12f)
                line(10f, 18f, 14f, 18f)
            }
            AppIcon.ExternalLink -> {
                poly(5f, 12f, 5f, 19f, 12f, 19f)
                line(11f, 5f, 19f, 5f)
                line(19f, 5f, 19f, 13f)
                line(19f, 5f, 10f, 14f)
            }
        }
    }
}