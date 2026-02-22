private fun startHiResSearch(query: String) {
        loader.visibility = View.VISIBLE
        statusText.text = "Escaneando YouTube Audio..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Paso 1: Buscamos el video en YouTube
                val encoded = URLEncoder.encode("$query audio", "UTF-8")
                // Usamos un buscador de música basado en YT (Invidious API) que es abierto
                val searchUrl = "https://inv.riverside.rocks/api/v1/search?q=$encoded&type=video"
                
                val connection = URL(searchUrl).openConnection() as HttpURLConnection
                val response = connection.inputStream.bufferedReader().readText()
                val results = org.json.JSONArray(response)

                if (results.length() > 0) {
                    val video = results.getJSONObject(0)
                    val videoId = video.getString("videoId")
                    val title = video.getString("title")
                    val author = video.getString("author")

                    // Paso 2: Obtenemos el link de audio directo
                    // Usamos un proxy de streaming que no requiere cuenta
                    val streamUrl = "https://inv.riverside.rocks/latest_version?id=$videoId&itag=140" 

                    withContext(Dispatchers.Main) {
                        loader.visibility = View.GONE
                        songInfo.text = title
                        statusText.text = "Canal: $author | Calidad: AAC 128kbps (Completa)"
                        playAudio(streamUrl)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        loader.visibility = View.GONE
                        statusText.text = "No se encontraron resultados en YouTube."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loader.visibility = View.GONE
                    statusText.text = "Error de Scraper: Reintentando conexión..."
                }
            }
        }
}ayer?.release()
    }
}
