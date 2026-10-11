import groovyx.net.http.HTTPBuilder
import groovyx.net.http.ContentType

import java.nio.file.Files
import java.nio.file.Path

class HttpService {

    private static final String USER_AGENT =
            'Mozilla/5.0 (compatible; CrawlerTISS/1.0)'

    String getText(String url) {
        HTTPBuilder http = new HTTPBuilder(url)
        http.headers['User-Agent'] = USER_AGENT

        http.get(contentType: ContentType.TEXT) { response, reader ->
            if (response.status < 200 || response.status >= 300) {
                throw new IOException("Erro HTTP ${response.status}: ${url}")
            }

            reader.text
        } as String
    }

    org.jsoup.nodes.Document getDocument(String url) {
        String html = getText(url)
        org.jsoup.Jsoup.parse(html, url)
    }

    void download(String url, Path destino) {
        HTTPBuilder http = new HTTPBuilder(url)
        http.headers['User-Agent'] = USER_AGENT

        byte[] conteudo = http.get(
                contentType: ContentType.BINARY
        ) { response, body ->

            if (response.status < 200 || response.status >= 300) {
                throw new IOException(
                        "Erro HTTP ${response.status} ao baixar ${url}"
                )
            }

            if (body instanceof byte[]) {
                return body
            }

            if (body instanceof InputStream) {
                return body.bytes
            }

            throw new IOException(
                    "Resposta binária inesperada para ${url}"
            )
        } as byte[]

        if (conteudo == null || conteudo.length == 0) {
            throw new IOException("Arquivo vazio: ${url}")
        }

        Files.createDirectories(destino.parent)
        Files.write(destino, conteudo)
    }
}