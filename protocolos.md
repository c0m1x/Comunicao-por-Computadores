# MissionLink (ML)

Comunicação fiável de missões sobre UDP.

### Estrutura das Mensagens

Cada mensagem ML inclui um **cabeçalho** e um **corpo (payload)**.

| Campo          | Tipo    | Descrição                                                                                                    |
| -------------- | ------- | -------------------------------------------------------------------------------------------------------------- |
| `type`       | enum    | Tipo da mensagem (`HELLO`, `RESPONSE`, `MISSION`, `ACK`, `PROGRESS`, `COMPLETED`, `ERROR`)                     |
| `seq`        | int     | Número de sequência                                                                                          |
| `tot`        | int     | Número total de fragmentos a enviar                                                                          |
| `suc`        | boolean | Indica o sucesso ou não da receção da mensagem total, ou uma resposta positiva/negativa                       |
| `mission_id` | int     | Identificador único da missão                                                                                |
| `idEmissor`  | int     | Identificador do emissor (0 = Nave-Mãe, >0 = Rover)                                                          |
| `idRecetor`  | int     | Identificador do recetor                                                                                      |
| `payload`    |         | Dados específicos da mensagem                                                                                 |

O conteúdo do payload varia consoante o tipo de mensagem:

- `HELLO`: vazio (apenas header com mission_id)
- `RESPONSE`: vazio (header.flagSucesso indica disponibilidade)
- `MISSION`: identificação, área geográfica (x1,y1,x2,y2), tarefa, duração, frequência das atualizações, data início, prioridade
- `ACK`: missing[] (fragmentos/progress perdidos), finalAck (indica fim de sessão)
- `PROGRESS`: idMissao, tempoDecorrido, progressoPercentagem
- `COMPLETED`: vazio (header.flagSucesso indica sucesso/falha)
- `ERROR`: idMissao, codigoErro, descricao, progressoAtual, bateria, posicaoX, posicaoY, timestampErro

### Mecanismo de Fiabilidade

1. Cada mensagem tem um **número de sequência (`seq`)** e o **número total de fragmentos** a ser transmitidos.
2. O recetor responde com um **ACK {seq}** e, no caso de se ter perdido algum pacote, indica que pacotes não recebeu para serem retransmitidos.
3. Se a nave mãe não receber o ACK dentro de um tempo limite, **retransmite** a mensagem.

---

### Fluxo de Comunicação

```
1. Nave-Mãe → HELLO (Inicia o contato com o rover, informando que há uma missão disponível (mission_id, seq=1))
2. Rover → RESPONSE (Confirma que está ativo e pronto para receber a missão (seq=1, suc=true))
3. Nave-Mãe → MISSION ((seq=2..N)  
   Envia todos os fragmentos da missão, contendo partes do payload.)
4. Rover → ACK (Analisa os fragmentos recebidos e envia um ACK seletivo:
     - Se faltarem fragmentos → ACK (missing=[lista de seq])  
     - Se todos foram recebidos → ACK (missing=[]))

5. [Opcional] Nave-Mãe → MISSION (Caso o ACK indique fragmentos faltantes, 
a Nave-Mãe retransmite apenas esses pacotes.) 

    - Rover → ACK   
        Confirma a receção completa (missing=[]).  
        Comunicação concluída com sucesso.
   
```

## Diagrama de sequência

```mermaid
sequenceDiagram
    participant R as Rover
    participant NM as Nave-Mãe

    Note over R,NM: ═══════ Fase 1: Handshake ═══════

    NM->>R: HELLO (seq=1, mission_id)
    
    alt Timeout sem RESPONSE (máx 3 tentativas)
        NM->>R: HELLO (seq=1, mission_id) [retry]
    end
    
    alt Rover disponível
        R-->>NM: RESPONSE (seq=1, suc=true)
    else Rover ocupado/indisponível
        R-->>NM: RESPONSE (seq=1, suc=false)
        Note over NM: Sessão terminada - procurar outro rover
    end

    Note over R,NM: ═══════ Fase 2: Envio da Missão ═══════

    alt Missão pequena (< 512 bytes)
        NM->>R: MISSION (seq=2, tot=1, payload=PayloadMissao)
    else Missão grande (fragmentada)
        loop Para cada fragmento i de N
            NM->>R: MISSION (seq=i+1, tot=N, payload=FragmentoPayload)
        end
    end

    Note over R: Rover verifica fragmentos recebidos

    alt Fragmentos em falta
        R-->>NM: ACK (seq=N+1, missing=[lista de seq])
        loop Retransmissão seletiva
            NM->>R: MISSION (seq=perdido, payload)
        end
        R-->>NM: ACK (seq=N+1, missing=[])
    else Todos recebidos
        R-->>NM: ACK (seq=N+1, missing=[])
    end

    Note over NM: Missão atribuída com sucesso
    Note over R: Rover reconstrói PayloadMissao e inicia execução

    Note over R,NM: ═══════ Fase 3: Reportagem de Progresso ═══════

    loop A cada intervaloAtualizacao (enquanto progresso < 100%)
        R->>NM: PROGRESS (seq=M, idMissao, tempoDecorrido, progresso%)
        
        alt Timeout sem ACK (máx 5 tentativas)
            R->>NM: PROGRESS (seq=M) [retry]
        end
        
        alt ACK com perdas detectadas
            NM-->>R: ACK (seq=M, missing=[seqs perdidos])
            loop Reenvio de PROGRESS perdidos
                R->>NM: PROGRESS (seq=perdido) [reenvio]
            end
        else ACK normal
            NM-->>R: ACK (seq=M, suc=true)
        end
        
        Note over NM: Atualiza estado da missão
    end

    Note over R,NM: ═══════ Fase 4: Conclusão ═══════

    alt Missão concluída com sucesso
        R->>NM: COMPLETED (seq=M+1, suc=true)
        
        alt Timeout sem ACK (máx 15 tentativas)
            R->>NM: COMPLETED [retry]
        end
        
        NM-->>R: ACK (seq=M+1, finalAck=true)
        NM-->>R: ACK (finalAck=true) [×3 para robustez]
        
        Note over NM: Estado missão → CONCLUIDA
        Note over R: Estado rover → DISPONIVEL
        
    else Erro durante execução (bateria crítica, falha hardware, etc.)
        R->>NM: ERROR (seq=M+1, codigoErro, descricao, progresso, bateria, posição)
        
        alt Timeout sem ACK (máx 15 tentativas)
            R->>NM: ERROR [retry]
        end
        
        NM-->>R: ACK (seq=M+1, finalAck=true)
        NM-->>R: ACK (finalAck=true) [×3 para robustez]
        
        Note over NM: Estado missão → FALHADA
        Note over R: Estado rover → FALHA
    end

    Note over R,NM: ═══════ Sessão Terminada ═══════
```

### Notas de Implementação

**Timeouts e Retries:**
- HELLO/RESPONSE: timeout 5s, máx 3 tentativas
- MISSION/ACK: timeout 5s, máx 3 tentativas
- PROGRESS: timeout 3s, máx 5 tentativas
- COMPLETED/ERROR: timeout 3s, máx 15 tentativas (mensagens críticas)

**Fragmentação:**
- Tamanho máximo por fragmento: 512 bytes
- Campos serializados com nome identificador para reconstrução
- Campos grandes são subdivididos com índice de parte

**ACK Final:**
- Flag `finalAck=true` indica ao rover para parar retransmissões
- Enviado 3 vezes consecutivas para garantir entrega (99.9% assumindo 10% perda)
