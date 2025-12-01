package lib.mensagens;

import lib.CampoSerializado;
import lib.mensagens.payloads.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários para SerializadorUDP.
 * Testa serialização, fragmentação, agregação e reconstrução de payloads.
 */
class SerializadorUDPTest {
    
    private SerializadorUDP serializador;
    
    @BeforeEach
    void setUp() {
        serializador = new SerializadorUDP();
    }
    
    // ==================== TESTES DE SERIALIZAÇÃO DE PAYLOADS ====================
    
    @Test
    @DisplayName("Serializar PayloadMissao deve criar todos os campos esperados")
    void testSerializarMissao() {
        PayloadMissao missao = criarMissaoTeste();
        
        List<CampoSerializado> campos = SerializadorUDP.serializarPayload(missao);
        
        assertEquals(10, campos.size(), "PayloadMissao deve ter 10 campos");
        assertTrue(temCampo(campos, "idMissao"), "Deve ter campo idMissao");
        assertTrue(temCampo(campos, "x1"), "Deve ter campo x1");
        assertTrue(temCampo(campos, "y1"), "Deve ter campo y1");
        assertTrue(temCampo(campos, "x2"), "Deve ter campo x2");
        assertTrue(temCampo(campos, "y2"), "Deve ter campo y2");
        assertTrue(temCampo(campos, "tarefa"), "Deve ter campo tarefa");
        assertTrue(temCampo(campos, "duracaoMissao"), "Deve ter campo duracaoMissao");
        assertTrue(temCampo(campos, "intervaloAtualizacao"), "Deve ter campo intervaloAtualizacao");
        assertTrue(temCampo(campos, "inicioMissao"), "Deve ter campo inicioMissao");
        assertTrue(temCampo(campos, "prioridade"), "Deve ter campo prioridade");
    }
    
    @Test
    @DisplayName("Serializar PayloadProgresso deve criar todos os campos esperados")
    void testSerializarProgresso() {
        PayloadProgresso progresso = new PayloadProgresso(1, 120, 50.5f);
        
        List<CampoSerializado> campos = SerializadorUDP.serializarPayload(progresso);
        
        assertEquals(3, campos.size(), "PayloadProgresso deve ter 3 campos");
        assertTrue(temCampo(campos, "idMissao"), "Deve ter campo idMissao");
        assertTrue(temCampo(campos, "tempoDecorrido"), "Deve ter campo tempoDecorrido");
        assertTrue(temCampo(campos, "progressoPercentagem"), "Deve ter campo progressoPercentagem");
    }
    
    @Test
    @DisplayName("Serializar PayloadAck deve criar todos os campos esperados")
    void testSerializarAck() {
        PayloadAck ack = new PayloadAck();
        ack.missingCount = 2;
        ack.missing = new int[]{3, 5};
        
        List<CampoSerializado> campos = SerializadorUDP.serializarPayload(ack);
        
        assertEquals(2, campos.size(), "PayloadAck deve ter 2 campos");
        assertTrue(temCampo(campos, "missingCount"), "Deve ter campo missingCount");
        assertTrue(temCampo(campos, "missing"), "Deve ter campo missing");
    }
    
    @Test
    @DisplayName("Serializar PayloadErro deve criar todos os campos esperados")
    void testSerializarErro() {
        PayloadErro erro = new PayloadErro(1, PayloadErro.CodigoErro.ERRO_BATERIA_CRITICA, 
                                           "Detalhe extra", 45.0f, 8.5f, 10.0f, 20.0f);
        
        List<CampoSerializado> campos = SerializadorUDP.serializarPayload(erro);
        
        assertEquals(8, campos.size(), "PayloadErro deve ter 8 campos");
        assertTrue(temCampo(campos, "idMissao"), "Deve ter campo idMissao");
        assertTrue(temCampo(campos, "codigoErro"), "Deve ter campo codigoErro");
        assertTrue(temCampo(campos, "descricao"), "Deve ter campo descricao");
        assertTrue(temCampo(campos, "progressoAtual"), "Deve ter campo progressoAtual");
        assertTrue(temCampo(campos, "bateria"), "Deve ter campo bateria");
        assertTrue(temCampo(campos, "posicaoX"), "Deve ter campo posicaoX");
        assertTrue(temCampo(campos, "posicaoY"), "Deve ter campo posicaoY");
        assertTrue(temCampo(campos, "timestampErro"), "Deve ter campo timestampErro");
    }
    
    // ==================== TESTES DE FRAGMENTAÇÃO ====================
    
    @Test
    @DisplayName("Payload pequeno não deve precisar de fragmentação")
    void testPayloadPequenoSemFragmentacao() {
        PayloadProgresso progresso = new PayloadProgresso(1, 100, 25.0f);
        
        boolean precisa = SerializadorUDP.precisaFragmentacao(progresso, 512);
        
        assertFalse(precisa, "PayloadProgresso pequeno não deve precisar de fragmentação");
    }
    
    @Test
    @DisplayName("Fragmentar payload pequeno deve criar apenas 1 fragmento")
    void testFragmentarPayloadPequeno() {
        PayloadMissao missao = criarMissaoTeste();
        
        List<FragmentoPayload> fragmentos = SerializadorUDP.fragmentarPayload(missao, 512);
        
        assertEquals(1, fragmentos.size(), "Missão pequena deve caber em 1 fragmento");
        assertTrue(fragmentos.get(0).temDados(), "Fragmento deve ter dados");
    }
    
    @Test
    @DisplayName("Fragmentar payload com tarefa grande deve criar múltiplos fragmentos")
    void testFragmentarPayloadGrande() {
        PayloadMissao missao = criarMissaoTeste();
        // Criar tarefa grande (>512 bytes)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("Tarefa muito longa para testar fragmentação. ");
        }
        missao.tarefa = sb.toString();
        
        List<FragmentoPayload> fragmentos = SerializadorUDP.fragmentarPayload(missao, 512);
        
        assertTrue(fragmentos.size() > 1, "Missão com tarefa grande deve ter múltiplos fragmentos");
        for (FragmentoPayload frag : fragmentos) {
            assertTrue(frag.temDados(), "Cada fragmento deve ter dados");
            assertTrue(frag.tamanhoEstimado() <= 512, "Cada fragmento deve respeitar tamanho máximo");
        }
    }
    
    @Test
    @DisplayName("Fragmentar campo grande deve dividir em partes com índices corretos")
    void testFragmentarCampoGrande() {
        byte[] dadosGrandes = new byte[1500];
        for (int i = 0; i < dadosGrandes.length; i++) {
            dadosGrandes[i] = (byte) (i % 256);
        }
        CampoSerializado campoGrande = new CampoSerializado("dadosTeste", dadosGrandes);
        
        List<CampoSerializado> partes = SerializadorUDP.fragmentarCampoGrande(campoGrande, 512);
        
        assertTrue(partes.size() > 1, "Campo grande deve ser dividido em múltiplas partes");
        
        // Verificar que todas as partes têm o mesmo nome
        for (CampoSerializado parte : partes) {
            assertEquals("dadosTeste", parte.nome, "Todas as partes devem ter o mesmo nome");
            assertTrue(parte.isFragmentado(), "Partes devem estar marcadas como fragmentadas");
            assertEquals(partes.size(), parte.totalPartes, "Total de partes deve ser consistente");
        }
        
        // Verificar índices sequenciais
        for (int i = 0; i < partes.size(); i++) {
            assertEquals(i, partes.get(i).indiceParte, "Índices devem ser sequenciais");
        }
    }
    
    // ==================== TESTES DE AGREGAÇÃO E RECONSTRUÇÃO ====================
    
    @Test
    @DisplayName("Agregar fragmentos e reconstruir missão deve recuperar dados originais")
    void testAgregarEReconstruirMissao() {
        PayloadMissao original = criarMissaoTeste();
        
        // Fragmentar
        List<FragmentoPayload> fragmentos = SerializadorUDP.fragmentarPayload(original, 512);
        
        // Agregar
        for (FragmentoPayload frag : fragmentos) {
            serializador.agregarCampos(frag);
        }
        
        // Verificar completude
        assertTrue(serializador.missaoCompleta(), "Missão deve estar completa após agregar todos os fragmentos");
        
        // Reconstruir
        PayloadMissao reconstruida = serializador.reconstruirMissao();
        
        // Verificar valores
        assertEquals(original.idMissao, reconstruida.idMissao);
        assertEquals(original.x1, reconstruida.x1, 0.001f);
        assertEquals(original.y1, reconstruida.y1, 0.001f);
        assertEquals(original.x2, reconstruida.x2, 0.001f);
        assertEquals(original.y2, reconstruida.y2, 0.001f);
        assertEquals(original.tarefa, reconstruida.tarefa);
        assertEquals(original.duracaoMissao, reconstruida.duracaoMissao);
        assertEquals(original.intervaloAtualizacao, reconstruida.intervaloAtualizacao);
        assertEquals(original.inicioMissao, reconstruida.inicioMissao);
        assertEquals(original.prioridade, reconstruida.prioridade);
    }
    
    @Test
    @DisplayName("Agregar fragmentos fora de ordem deve reconstruir corretamente")
    void testAgregarFragmentosForaDeOrdem() {
        PayloadMissao original = criarMissaoTeste();
        original.tarefa = "Tarefa de teste com algum conteúdo";
        
        List<FragmentoPayload> fragmentos = SerializadorUDP.fragmentarPayload(original, 512);
        
        // Agregar em ordem inversa (se houver múltiplos)
        for (int i = fragmentos.size() - 1; i >= 0; i--) {
            serializador.agregarCampos(fragmentos.get(i));
        }
        
        assertTrue(serializador.missaoCompleta(), "Missão deve estar completa mesmo com fragmentos fora de ordem");
        
        PayloadMissao reconstruida = serializador.reconstruirMissao();
        assertEquals(original.tarefa, reconstruida.tarefa);
    }
    
    @Test
    @DisplayName("Reconstruir campo fragmentado deve juntar partes corretamente")
    void testReconstruirCampoFragmentado() {
        // Criar dados grandes
        byte[] dadosOriginais = new byte[1500];
        for (int i = 0; i < dadosOriginais.length; i++) {
            dadosOriginais[i] = (byte) (i % 256);
        }
        CampoSerializado campoGrande = new CampoSerializado("dadosTeste", dadosOriginais);
        
        // Fragmentar
        List<CampoSerializado> partes = SerializadorUDP.fragmentarCampoGrande(campoGrande, 512);
        
        // Agregar partes (em ordem aleatória)
        serializador.adicionarCampo(partes.get(partes.size() - 1)); // última
        serializador.adicionarCampo(partes.get(0)); // primeira
        for (int i = 1; i < partes.size() - 1; i++) {
            serializador.adicionarCampo(partes.get(i)); // meio
        }
        
        // Reconstruir
        byte[] reconstruidos = serializador.reconstruirBytes("dadosTeste");
        
        // Verificar
        assertArrayEquals(dadosOriginais, reconstruidos, "Dados reconstruídos devem ser iguais aos originais");
    }
    
    @Test
    @DisplayName("Reconstruir PayloadProgresso deve recuperar dados originais")
    void testReconstruirProgresso() {
        PayloadProgresso original = new PayloadProgresso(42, 3600, 75.5f);
        
        List<FragmentoPayload> fragmentos = SerializadorUDP.fragmentarPayload(original, 512);
        for (FragmentoPayload frag : fragmentos) {
            serializador.agregarCampos(frag);
        }
        
        assertTrue(serializador.progressoCompleto());
        
        PayloadProgresso reconstruido = serializador.reconstruirProgresso();
        
        assertEquals(original.idMissao, reconstruido.idMissao);
        assertEquals(original.tempoDecorrido, reconstruido.tempoDecorrido);
        assertEquals(original.progressoPercentagem, reconstruido.progressoPercentagem, 0.001f);
    }
 
    
    // ==================== TESTES DE VERIFICAÇÃO DE COMPLETUDE ====================
    
    @Test
    @DisplayName("Missão incompleta deve retornar false em missaoCompleta()")
    void testMissaoIncompleta() {
        PayloadMissao missao = criarMissaoTeste();
        List<CampoSerializado> campos = SerializadorUDP.serializarPayload(missao);
        
        // Adicionar apenas alguns campos
        serializador.adicionarCampo(campos.get(0)); // idMissao
        serializador.adicionarCampo(campos.get(1)); // x1
        
        assertFalse(serializador.missaoCompleta(), "Missão com campos em falta não deve estar completa");
    }
    
    @Test
    @DisplayName("Campo fragmentado incompleto deve retornar false em campoCompleto()")
    void testCampoFragmentadoIncompleto() {
        byte[] dadosGrandes = new byte[1500];
        CampoSerializado campoGrande = new CampoSerializado("teste", dadosGrandes);
        List<CampoSerializado> partes = SerializadorUDP.fragmentarCampoGrande(campoGrande, 512);
        
        // Adicionar apenas primeira parte
        serializador.adicionarCampo(partes.get(0));
        
        assertFalse(serializador.campoCompleto("teste"), "Campo com partes em falta não deve estar completo");
    }
    
    // ==================== TESTES DE SERIALIZAÇÃO DE OBJETOS ====================
    
    @Test
    @DisplayName("Serializar e deserializar MensagemUDP deve preservar dados")
    void testSerializarDeserializarMensagem() throws IOException {
        MensagemUDP original = new MensagemUDP();
        original.header.tipo = lib.TipoMensagem.MSG_HELLO;
        original.header.idEmissor = 0;
        original.header.idRecetor = 1;
        original.header.idMissao = 42;
        original.header.seq = 1;
        original.header.totalFragm = 1;
        original.header.flagSucesso = true;
        original.payload = null;
        
        byte[] dados = serializador.serializarObjeto(original);
        MensagemUDP reconstruida = SerializadorUDP.deserializarMensagem(dados, dados.length);
        
        assertNotNull(reconstruida);
        assertEquals(original.header.tipo, reconstruida.header.tipo);
        assertEquals(original.header.idEmissor, reconstruida.header.idEmissor);
        assertEquals(original.header.idRecetor, reconstruida.header.idRecetor);
        assertEquals(original.header.idMissao, reconstruida.header.idMissao);
        assertEquals(original.header.seq, reconstruida.header.seq);
        assertEquals(original.header.flagSucesso, reconstruida.header.flagSucesso);
    }
    
    @Test
    @DisplayName("Deserializar dados inválidos deve retornar null")
    void testDeserializarDadosInvalidos() {
        byte[] dadosInvalidos = new byte[]{0x00, 0x01, 0x02, 0x03};
        
        MensagemUDP resultado = SerializadorUDP.deserializarMensagem(dadosInvalidos, dadosInvalidos.length);
        
        assertNull(resultado, "Dados inválidos devem resultar em null");
    }
    
    // ==================== TESTES DE LIMPAR E ESTADO ====================
    
    @Test
    @DisplayName("Limpar deve remover todos os campos agregados")
    void testLimpar() {
        PayloadMissao missao = criarMissaoTeste();
        List<FragmentoPayload> fragmentos = SerializadorUDP.fragmentarPayload(missao, 512);
        
        for (FragmentoPayload frag : fragmentos) {
            serializador.agregarCampos(frag);
        }
        
        assertTrue(serializador.numeroCampos() > 0, "Deve ter campos antes de limpar");
        
        serializador.limpar();
        
        assertEquals(0, serializador.numeroCampos(), "Deve ter 0 campos após limpar");
        assertFalse(serializador.missaoCompleta(), "Missão não deve estar completa após limpar");
    }
    
    @Test
    @DisplayName("temCampo deve retornar corretamente")
    void testTemCampo() {
        assertFalse(serializador.temCampo("idMissao"), "Não deve ter campo antes de agregar");
        
        PayloadMissao missao = criarMissaoTeste();
        List<CampoSerializado> campos = SerializadorUDP.serializarPayload(missao);
        serializador.adicionarCampo(campos.get(0)); // idMissao
        
        assertTrue(serializador.temCampo("idMissao"), "Deve ter campo após agregar");
        assertFalse(serializador.temCampo("campoInexistente"), "Não deve ter campo inexistente");
    }
    
    // ==================== TESTES DE TAMANHO ====================
    
    @Test
    @DisplayName("calcularTamanhoTotal deve somar tamanhos dos campos")
    void testCalcularTamanhoTotal() {
        PayloadMissao missao = criarMissaoTeste();
        List<CampoSerializado> campos = SerializadorUDP.serializarPayload(missao);
        
        int tamanhoTotal = SerializadorUDP.calcularTamanhoTotal(campos);
        
        assertTrue(tamanhoTotal > 0, "Tamanho total deve ser maior que 0");
        
        // Verificar que é a soma dos tamanhos individuais
        int somaTamanhos = 0;
        for (CampoSerializado c : campos) {
            somaTamanhos += c.tamanho();
        }
        assertEquals(somaTamanhos, tamanhoTotal);
    }
    
    // ==================== MÉTODOS AUXILIARES ====================
    
    private PayloadMissao criarMissaoTeste() {
        PayloadMissao missao = new PayloadMissao();
        missao.idMissao = 1;
        missao.x1 = 10.0f;
        missao.y1 = 20.0f;
        missao.x2 = 30.0f;
        missao.y2 = 40.0f;
        missao.tarefa = "Explorar área";
        missao.duracaoMissao = 3600;
        missao.intervaloAtualizacao = 10;
        missao.inicioMissao = System.currentTimeMillis() / 1000;
        missao.prioridade = 3;
        return missao;
    }
    
    private boolean temCampo(List<CampoSerializado> campos, String nome) {
        for (CampoSerializado c : campos) {
            if (c.nome.equals(nome)) {
                return true;
            }
        }
        return false;
    }
}
