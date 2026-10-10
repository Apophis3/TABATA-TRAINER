package com.tuapp.tabatatrainer.ui.freeride

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuapp.tabatatrainer.service.LapData
import com.tuapp.tabatatrainer.util.laps.LapTableModel

/** Anchos de las 9 columnas que se desplazan, en el orden de [LapTableModel.SCROLL_HEADERS] */
private val SCROLL_COLUMN_WIDTHS: List<Dp> = listOf(46.dp, 44.dp, 58.dp, 48.dp, 40.dp, 52.dp, 50.dp, 76.dp, 70.dp)

/** Columna nº fija (bandera + número global) */
private val FIXED_COLUMN_WIDTH = 46.dp

private val ROW_HEIGHT = 28.dp
private val HEADER_HEIGHT = 30.dp

/**
 * Ventana 4: tabla completa de vueltas (spec 006, R8). Densidad máxima, estilo R5 (AUTO tenue / MANUAL negrita).
 *
 * - La columna nº NO se desplaza; las otras 9 se desplazan en horizontal todas a la vez: cada fila (y la cabecera)
 *   comparte el mismo `ScrollState`, así que arrastrar cualquiera mueve la tabla entera.
 * - Scroll vertical independiente (`LazyColumn`).
 * - Relevo al pager (RF-8.6): `horizontalScroll` reparte el sobrante del gesto al padre; en el borde de la tabla el
 *   `HorizontalPager` recibe el arrastre y cambia de ventana. Si cabe entera (apaisado), todo el gesto va al pager.
 */
@Composable
internal fun LapTable(
    laps: List<LapData>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val rows = remember(laps) { LapTableModel.rows(laps) }
    val hScroll = rememberScrollState()
    val headerColor = Color.White.copy(alpha = 0.7f)
    val textSize = 12.sp

    Column(modifier = modifier) {
        // Cabecera
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HEADER_HEIGHT)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                LapTableModel.FIXED_HEADER,
                modifier = Modifier.width(FIXED_COLUMN_WIDTH),
                color = headerColor,
                fontSize = textSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Row(Modifier.horizontalScroll(hScroll)) {
                LapTableModel.SCROLL_HEADERS.forEachIndexed { i, title ->
                    Text(
                        title,
                        modifier = Modifier.width(SCROLL_COLUMN_WIDTHS[i]),
                        color = headerColor,
                        fontSize = textSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        if (rows.isEmpty()) {
            Text(
                "No hay vueltas registradas",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp)
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            contentPadding = contentPadding
        ) {
            items(rows, key = { it.order }) { row ->
                val color = Color(row.style.textColorArgb)
                val weight = if (row.style.bold) FontWeight.Bold else FontWeight.Normal
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT)
                        .background(Color(row.style.rowBackgroundArgb)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Columna fija: bandera (manual) + nº global
                    Row(
                        modifier = Modifier.width(FIXED_COLUMN_WIDTH),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (row.style.showFlag) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = "Vuelta manual",
                                tint = color,
                                modifier = Modifier.size(11.dp)
                            )
                            Box(Modifier.width(2.dp))
                        }
                        Text("${row.order}", color = color, fontWeight = weight, fontSize = textSize, maxLines = 1)
                    }
                    // Celdas que se desplazan, sincronizadas con la cabecera y con el resto de filas
                    Row(Modifier.horizontalScroll(hScroll), verticalAlignment = Alignment.CenterVertically) {
                        row.cells.forEachIndexed { i, text ->
                            Text(
                                text,
                                modifier = Modifier.width(SCROLL_COLUMN_WIDTHS[i]),
                                color = color,
                                fontWeight = weight,
                                fontSize = textSize,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
