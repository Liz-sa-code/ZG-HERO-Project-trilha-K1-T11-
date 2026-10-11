
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.Normalizer
import java.net.URLDecoder

class CrawlerANS {

    static final String PAGINA_TISS =
            'https://www.gov.br/ans/pt-br/assuntos/prestadores/padrao-para-troca-de-informacao-de-saude-suplementar-2013-tiss'

    static final String PAGINA_HISTORICO =
            PAGINA_TISS +
                    '/padrao-tiss-historico-das-versoes-dos-componentes-do-padrao-tiss'

    static final String PAGINA_TABELAS =
            PAGINA_TISS + '/padrao-tiss-tabelas-relacionadas'

    final HttpService http = new HttpService()

    final Path pastaDownloads = Paths.get('Downloads')
    final Path pastaTiss =
            pastaDownloads.resolve('Arquivos_padrao_TISS')
    final Path pastaErros =
            pastaDownloads.resolve('Tabela_erros')

    Map executar() {
        Files.createDirectories(pastaTiss)
        Files.createDirectories(pastaErros)

        println '\n[1/3] Baixando componente de comunicação...'
        List<Map> componentes = baixarComponenteComunicacao()

        println '\n[2/3] Extraindo histórico das versões...'
        int historico = extrairHistorico()

        println '\n[3/3] Baixando tabela de erros...'
        String tabelaErros = baixarTabelaErros()

        println '\n========== RESULTADO =========='
        println "Arquivos do componente: ${componentes.size()}"
        println "Registros do histórico: ${historico}"
        println "Tabela de erros: ${tabelaErros ?: 'Não encontrada'}"
        println "Pasta de saída: ${pastaDownloads.toAbsolutePath()}"

        return [
                componentes: componentes,
                historico: historico,
                tabelaErros: tabelaErros
        ]
    }

    List<Map> baixarComponenteComunicacao() {
        Document principal = http.getDocument(PAGINA_TISS)

        // Procura a página da versão vigente do padrão TISS.
        Element linkVersao = principal.select('a[href]').find {
            String texto = normalizar(it.text())

            texto.contains('acessar a versao') &&
                    texto.contains('20')
        }

        Document paginaVersao

        if (linkVersao != null) {
            String urlVersao = linkVersao.absUrl('href')
            println "Página da versão vigente: ${urlVersao}"
            paginaVersao = http.getDocument(urlVersao)
        } else {
            // Algumas versões do site podem exibir os arquivos
            // diretamente na página principal.
            paginaVersao = principal
            println 'Procurando os arquivos na página principal.'
        }

        Element linkComunicacao =
                paginaVersao.select('a[href]').find { Element link ->
                    String texto = normalizar(link.text())
                    String url = link.absUrl('href').toLowerCase()

                    texto.contains('componente de comunicacao') &&
                            (
                                    texto.contains('.zip') ||
                                            url.contains('.zip') ||
                                            url.contains('@@download/file')
                            )
                }

        if (linkComunicacao == null) {
            throw new IOException(
                    'Não foi encontrado o ZIP do componente de comunicação. ' +
                            'Verifique os links publicados na versão vigente da ANS.'
            )
        }

        String url = linkComunicacao.absUrl('href')

        Path destino = destinoUnico(
                pastaTiss,
                'Componente_Comunicacao.zip'
        )

        http.download(url, destino)

        validarArquivo(destino)

        println "Baixado: ${destino.toAbsolutePath()}"

        return [[
                        url: url,
                        nome: destino.fileName.toString()
                ]]
    }

    int extrairHistorico() {
        Document doc = http.getDocument(PAGINA_HISTORICO)
        List<Map> registros = []

        doc.select('table').each { Element tabela ->
            List<Element> linhas = tabela.select('tr')

            if (linhas.isEmpty()) {
                return
            }

            // O cabeçalho pode não ser a primeira linha.
            int indiceCabecalho = linhas.findIndexOf {
                Element linha ->

                    List<String> textos =
                            linha.select('th, td').collect {
                                normalizar(it.text())
                            }

                    indiceColuna(textos, ['competencia']) >= 0 &&
                            indiceColuna(textos, ['publicacao']) >= 0 &&
                            indiceColuna(
                                    textos,
                                    ['inicio de vigencia']
                            ) >= 0
            }

            if (indiceCabecalho < 0) {
                return
            }

            List<String> cabecalhos =
                    linhas[indiceCabecalho].select('th, td').collect {
                        normalizar(it.text())
                    }

            int competenciaIdx =
                    indiceColuna(cabecalhos, ['competencia'])

            int publicacaoIdx =
                    indiceColuna(cabecalhos, ['publicacao'])

            int vigenciaIdx =
                    indiceColuna(cabecalhos, ['inicio de vigencia'])

            linhas.drop(indiceCabecalho + 1).each {
                Element linha ->

                    List<String> celulas =
                            linha.select('td, th').collect {
                                it.text().trim()
                            }

                    int maiorIndice = [
                            competenciaIdx,
                            publicacaoIdx,
                            vigenciaIdx
                    ].max()

                    if (celulas.size() <= maiorIndice) {
                        return
                    }

                    String competencia = celulas[competenciaIdx]

                    if (desdeJaneiro2016(competencia)) {
                        registros << [
                                competencia: competencia,
                                publicacao: celulas[publicacaoIdx],
                                vigencia: celulas[vigenciaIdx]
                        ]
                    }
            }
        }

        registros = registros.unique {
            "${it.competencia}|${it.publicacao}|${it.vigencia}"
        }

        Path arquivoCsv =
                pastaDownloads.resolve('historico_tiss.csv')

        arquivoCsv.toFile().withWriter('UTF-8') { writer ->
            writer.writeLine(
                    'competencia;publicacao;inicio_vigencia'
            )

            registros.each { registro ->
                writer.writeLine([
                        csv(registro.competencia),
                        csv(registro.publicacao),
                        csv(registro.vigencia)
                ].join(';'))
            }
        }

        println "CSV criado: ${arquivoCsv.toAbsolutePath()}"
        println "Registros encontrados: ${registros.size()}"

        if (registros.isEmpty()) {
            println(
                    'ATENÇÃO: nenhum registro foi extraído. ' +
                            'Confira a estrutura da tabela da ANS.'
            )
        }

        return registros.size()
    }

    String baixarTabelaErros() {
        Document doc = http.getDocument(PAGINA_TABELAS)

        Element linkTabela =
                doc.select('a[href]').find { Element link ->
                    String texto = normalizar(link.text())
                    String contexto =
                            normalizar(link.parent()?.text() ?: '')

                    texto.contains(
                            'tabela de erros no envio para a ans'
                    ) ||
                            (
                                    contexto.contains(
                                            'tabela de erros no envio para a ans'
                                    ) &&
                                            (
                                                    texto.contains('baixar') ||
                                                            texto.contains('.xlsx')
                                            )
                            )
                }

        if (linkTabela == null) {
            throw new IOException(
                    'Não foi encontrado o link da tabela de erros na ANS.'
            )
        }

        String url = linkTabela.absUrl('href')

        Path destino = destinoUnico(
                pastaErros,
                'Tabela_erros_envio_ANS.xlsx'
        )

        http.download(url, destino)

        validarArquivo(destino)

        println "Tabela de erros salva em: ${destino.toAbsolutePath()}"

        return destino.toString()
    }

    private static int indiceColuna(
            List<String> cabecalhos,
            List<String> alternativas
    ) {
        for (int i = 0; i < cabecalhos.size(); i++) {
            if (alternativas.any { alternativa ->
                cabecalhos[i].contains(normalizar(alternativa))
            }) {
                return i
            }
        }

        return -1
    }

    private static boolean desdeJaneiro2016(String texto) {
        String valor = normalizar(texto)

        Map<String, Integer> meses = [
                jan: 1,
                janeiro: 1,
                fev: 2,
                fevereiro: 2,
                mar: 3,
                marco: 3,
                abr: 4,
                abril: 4,
                mai: 5,
                maio: 5,
                jun: 6,
                junho: 6,
                jul: 7,
                julho: 7,
                ago: 8,
                agosto: 8,
                set: 9,
                setembro: 9,
                out: 10,
                outubro: 10,
                nov: 11,
                novembro: 11,
                dez: 12,
                dezembro: 12
        ]

        // Exemplos: jul/2026, janeiro de 2016.
        def mesAno =
                valor =~ /([a-z]+)[\s\/.-]*(?:de\s*)?(20\d{2})/

        if (mesAno.find()) {
            String nomeMes = mesAno.group(1)

            Integer mes = meses.find { nome, numero ->
                nomeMes.startsWith(nome)
            }?.value

            int ano = mesAno.group(2).toInteger()

            return mes != null &&
                    (ano > 2016 || (ano == 2016 && mes >= 1))
        }

        // Exemplos: 01/2016, 2016-01.
        def numerico =
                valor =~ /(?:(\d{1,2})[\/.-](20\d{2})|(20\d{2})[\/.-](\d{1,2}))/

        if (numerico.find()) {
            int ano = (
                    numerico.group(2) ?: numerico.group(3)
            ).toInteger()

            int mes = (
                    numerico.group(1) ?: numerico.group(4)
            ).toInteger()

            return mes >= 1 && mes <= 12 &&
                    (ano > 2016 || (ano == 2016 && mes >= 1))
        }

        return false
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(
                texto ?: '',
                Normalizer.Form.NFD
        )
                .replaceAll(/\p{M}/, '')
                .toLowerCase(Locale.ROOT)
                .replaceAll(/\s+/, ' ')
                .trim()
    }

    private static Path destinoUnico(Path pasta, String nome) {
        Path destino = pasta.resolve(nome)
        int contador = 2

        while (Files.exists(destino)) {
            int ponto = nome.lastIndexOf('.')

            String base = ponto > 0
                    ? nome.substring(0, ponto)
                    : nome

            String extensao = ponto > 0
                    ? nome.substring(ponto)
                    : ''

            destino = pasta.resolve(
                    "${base}_${contador}${extensao}"
            )

            contador++
        }

        return destino
    }

    private static void validarArquivo(Path arquivo) {
        if (!Files.exists(arquivo) ||
                Files.size(arquivo) == 0) {
            throw new IOException(
                    "O arquivo não foi baixado corretamente: ${arquivo}"
            )
        }
    }

    private static String csv(String valor) {
        String texto = (valor ?: '').replace('"', '""')

        if (texto.contains(';') ||
                texto.contains('"') ||
                texto.contains('\n') ||
                texto.contains('\r')) {
            return "\"${texto}\""
        }

        return texto
    }
}
