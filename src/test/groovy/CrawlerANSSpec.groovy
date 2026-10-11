import spock.lang.Specification

class CrawlerANSSpec extends Specification {

    def "deve configurar as páginas públicas da ANS"() {
        expect:
        CrawlerANS.PAGINA_TISS.startsWith(
                'https://www.gov.br/ans/'
        )

        CrawlerANS.PAGINA_HISTORICO.contains(
                'historico-das-versoes'
        )

        CrawlerANS.PAGINA_TABELAS.contains(
                'tabelas-relacionadas'
        )
    }

    def "deve definir os diretórios de saída"() {
        given:
        CrawlerANS crawler = new CrawlerANS()

        expect:
        crawler.pastaDownloads.toString() == 'Downloads'
        crawler.pastaTiss.toString().contains('Arquivos_padrao_TISS')
        crawler.pastaErros.toString().contains('Tabela_erros')
    }
}