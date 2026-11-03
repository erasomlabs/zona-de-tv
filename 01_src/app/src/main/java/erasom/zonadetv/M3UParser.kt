package erasom.zonadetv

import android.util.Log

object M3UParser {
    fun parse(text: String): List<Channel> {
        val lines = text.lines()
        val channels = mutableListOf<Channel>()
        var currentName = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                // Extraer el nombre después de la última coma
                val commaIndex = trimmed.lastIndexOf(',')
                if (commaIndex != -1) {
                    currentName = trimmed.substring(commaIndex + 1).trim()
                } else {
                    currentName = "Canal"
                }
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                currentName.takeIf { it.isNotEmpty() }?.let { name ->
                    channels.add(Channel(name, trimmed))
                }
                currentName = ""
            }
        }
        return channels
    }
}
