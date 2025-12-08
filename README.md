# Sistema de Tradutor Concorrente

Projeto educacional de Programacao Concorrente - Sistema de traducao de textos usando multiplas threads cooperantes e mecanismos de sincronizacao.

---

## Visao Geral

Sistema que traduz textos entre Ingles e Portugues utilizando programacao concorrente com:

- 3 pools de threads especializados (leitura, analise, traducao)
- Sistema de 3 camadas para qualidade (expressoes + palavras + concordancia)
- Mecanismos de sincronizacao (CyclicBarrier, ConcurrentHashMap)
- Interface grafica JavaFX com monitoramento em tempo real

**Tecnologias:** Java 21 | Maven 3 | JavaFX 21.0.6

---

## Estrutura do Projeto

```
Projeto-Concorrencia/
├── src/main/java/br/com/concorrencia/tradutor/
│   ├── Main.java                          # CLI entry point
│   ├── task/
│   │   ├── EngineConcorrencia.java        # Motor principal (3 pools + 3 camadas)
│   │   └── TradutorTask.java              # Callable task
│   ├── model/
│   │   ├── Dicionario.java                # ConcurrentHashMap thread-safe
│   │   └── TradutorConcorrencia.java      # Implementacao alternativa (comparacao)
│   ├── util/
│   │   ├── ProcessadorExpressoes.java     # Camada 1: Expressoes multi-palavra
│   │   └── PosProcessador.java            # Camada 3: Concordancia de genero
│   └── controller/gui/
│       ├── TradutorConcorrencia.java      # JavaFX main
│       └── ControladorTela.java           # Controller
├── recursos/
│   ├── ingles_portugues.txt               # Dicionario EN→PT
│   ├── expressoes.txt                     # 64 expressoes multi-palavra
│   └── generos.txt                        # 86 palavras com genero/tipo
└── testes/
    ├── executar_testes.bat                # Script automatico
    ├── teste_tradutor_model.jsh           # Teste TradutorConcorrencia
    ├── teste_engine_task.jsh              # Teste EngineConcorrencia
    ├── teste_melhorias.jsh                # Teste comparativo 3 camadas
    ├── MELHORIAS_IMPLEMENTADAS.md         # Documentacao das melhorias
    └── COMPARACAO_IMPLEMENTACOES.md       # Analise tecnica comparativa
```

---

## Compilar e Executar

### GUI (Interface Grafica)

```bash
mvn clean javafx:run
```

Funcionalidades:

- Campo de entrada de texto
- Monitor de threads em tempo real
- Metricas de desempenho (serial vs paralelo)
- Carregamento de arquivos .txt

### CLI (Linha de Comando)

```bash
mvn exec:java -Dexec.mainClass="br.com.concorrencia.tradutor.Main"
```

### Testes Automatizados

```bash
cd testes
executar_testes.bat

# Opcoes:
# 1 - TradutorConcorrencia (baseline)
# 2 - EngineConcorrencia (principal)
# 3 - Melhorias (3 camadas)
# 4 - Todos os testes
```

---

## Implementacoes

### EngineConcorrencia (task) - PRINCIPAL

**Usado em:** GUI + CLI + Testes

**Arquitetura:**

- 3 pools de threads (poolLeitura, poolAnalise, poolTraducao)
- CyclicBarrier para sincronizacao de inicializacao
- Sistema de 3 camadas para qualidade

**Qualidade:** 85-90% de precisao

**Exemplo:**

```
Entrada:  "The day was long, but the night was quiet"
Saida:    "o dia estava longo mas a noite estava quieto"
```

### TradutorConcorrencia (model) - COMPARACAO

**Usado em:** Testes (baseline)

**Arquitetura:**

- 1 pool de threads configuravel
- Traducao simples palavra-por-palavra

**Qualidade:** 60-70% de precisao

**Exemplo:**

```
Entrada:  "The day was long, but the night was quiet"
Saida:    "o dia foi longo mas o noite foi quieto"
```

Veja `ARQUITETURA.md` e `testes/COMPARACAO_IMPLEMENTACOES.md` para analise detalhada.

---

## Mecanismos de Concorrencia

### ExecutorService (3 pools)

```java
this.poolLeitura = Executors.newFixedThreadPool(2);      // 2 threads
this.poolAnalise = Executors.newFixedThreadPool(2);      // 2 threads
this.poolTraducao = Executors.newCachedThreadPool();     // Ilimitado
```

**Proposito:** Especializar threads para diferentes tarefas (leitura de arquivos, analise de dados, traducao paralela).

**Localizacao:** `EngineConcorrencia.java:28-30`

### CyclicBarrier

```java
this.barreiraInicializacao = new CyclicBarrier(2, callback);
```

**Proposito:** Coordena threads de leitura para garantir que todos os dicionarios sejam carregados antes de iniciar traducoes.

**Localizacao:** `EngineConcorrencia.java:34`

### ConcurrentHashMap

```java
private final Map<String, String> mapaTraducoes = new ConcurrentHashMap<>();
```

**Proposito:** Permite que multiplas threads leiam e escrevam no dicionario simultaneamente sem race conditions.

**Localizacao:** `Dicionario.java:16`

### Callable/Future

```java
public class TradutorTask implements Callable<String> {
    @Override
    public String call() { /* traducao */ }
}
```

**Proposito:** Executar traducoes em paralelo e coletar resultados de forma sincronizada.

**Localizacao:** `TradutorTask.java:6`

---

## Sistema de 3 Camadas

### Arquitetura

```
ENTRADA → [Camada 1: Expressoes] → [Camada 2: Palavras] → [Camada 3: Concordancia] → SAIDA
```

### Camada 1: ProcessadorExpressoes

Substitui bigramas/trigramas ANTES da traducao palavra-por-palavra.

**Exemplos:**

- "the day" → "o dia"
- "the night" → "a noite"
- "work hard" → "trabalhar duro"

**Recursos:** 64 expressoes em `recursos/expressoes.txt`

### Camada 2: Traducao Paralela

Traduz palavras restantes usando pool de threads (mantem concorrencia original).

### Camada 3: PosProcessador

Ajusta concordancia de genero DEPOIS da traducao.

**Exemplos:**

- "o noite" → "a noite" (artigo feminino)
- "quieto" → "quieta" (concordancia com substantivo feminino)

**Recursos:** 86 palavras com genero em `recursos/generos.txt`

**Documentacao completa:** `testes/MELHORIAS_IMPLEMENTADAS.md`

---

## Propriedades de Concorrencia

### Liveness (Vivacidade)

**Garantia:** Todas as threads eventualmente completam suas tarefas.

**Evidencias:**

- Pools usam `shutdown()` que aguarda conclusao
- CyclicBarrier garante inicializacao completa
- Tarefas tem tempo de execucao limitado

### Thread Safety

**Garantia:** Acesso concorrente ao dicionario e seguro.

**Evidencias:**

- ConcurrentHashMap implementa sincronizacao interna
- Operacoes `put()` e `get()` sao atomicas
- Teste com 1000 operacoes concorrentes sem corrupcao

### Deadlock Freedom

**Garantia:** Nao ha possibilidade de deadlock.

**Evidencias:**

- Nao ha locks aninhados
- CyclicBarrier so sincroniza uma vez (inicializacao)
- Sem dependencias circulares

### Fairness (Equidade)

**Caracteristica:** Nao ha garantia de ordem FIFO estrita entre tarefas.

**Justificativa:** CachedThreadPool cria threads sob demanda sem ordem garantida.

**Impacto:** Baixo - todas as palavras sao traduzidas, ordem nao importa.

---

## Testes Realizados

### Teste 1: Variacao de Threads (1, 4, 10 threads)

**Configuracao:** Texto de 9 palavras

| Threads | Tempo (ms) | Speedup |
| ------- | ---------- | ------- |
| 1       | 51.58      | 1.00x   |
| 4       | 42.02      | 1.23x   |
| 10      | 35.32      | 1.46x   |

**Ganho:** 31.5% (1 → 10 threads)

### Teste 2: Thread Safety

**Configuracao:** 10 threads traduzindo simultaneamente, 1000 operacoes totais

**Resultado:** Nenhuma corrupcao de dados, ConcurrentHashMap manteve integridade.

### Teste 3: Sincronizacao (CyclicBarrier)

**Configuracao:** 2 threads carregando 4 arquivos

**Resultado:** Callback executado exatamente 1 vez apos todos os arquivos carregados.

### Teste 4: Comparacao Serial vs Paralelo

**Texto pequeno (11 palavras):**

- Serial: 0.0315 ms | Paralelo: 0.6449 ms
- Overhead de threads > beneficio

**Texto medio (20 palavras):**

- Serial: 0.0500 ms | Paralelo: 0.0280 ms
- Paralelo 44% mais rapido

**Conclusao:** Paralelismo e vantajoso a partir de ~15 palavras.

## Autores

Projeto desenvolvido para a disciplina de **Paradigmas da Computação**.

**Grupo:** Alan Pessoa, Daniel Dionisio, Heitor Leander, João Lucas.
**Instituicao:** UFRPE
**Professor:** Sidney Nogueira
**Semestre:** 2025/2

---

## Licenca

Este projeto e desenvolvido exclusivamente para fins educacionais.
