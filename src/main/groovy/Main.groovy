/*
 * Autora: Liz Soares
 * Projeto: Crawler TISS - ANS
 */

class Main {

    static void main(String[] args) {
        println '===================================='
        println '       CRAWLER TISS - ANS'
        println '       Autora: Liz Soares'
        println '===================================='

        try {
            CrawlerANS crawler = new CrawlerANS()
            Map resultado = crawler.executar()

            println '\n========== RESULTADO =========='
            println "Arquivos do componente: ${resultado.componentes.size()}"
            println "Registros do histórico: ${resultado.historico}"
            println "Tabela de erros: ${resultado.tabelaErros ?: 'Não encontrada'}"
            println "Pasta de saída: ${new File('Downloads').absolutePath}"

        } catch (Exception e) {
            System.err.println "Erro durante a execução: ${e.message}"
            e.printStackTrace()
        }
    }
}