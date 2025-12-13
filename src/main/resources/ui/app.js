/**
 * Detecção automática do endpoint da API da Nave-Mãe.
 * Prioridade:
 *  1. Override via URL →  ?api=http://IP:PORTA
 *  2. Ambiente CORE-EMU → host 10.0.x.x
 *  3. Ambiente local → localhost:8080
 */
function detectApiBase() {
    const urlParams = new URLSearchParams(window.location.search);
    const apiOverride = urlParams.get('api');

    if (apiOverride) {
        console.log('[Config] API override detectado:', apiOverride);
        return apiOverride;
    }

    const hostname = window.location.hostname;
    console.log('[Config] Hostname detectado:', hostname);

    // Ambiente CORE
    if (hostname.startsWith('10.0.')) {
        const coreApi = 'http://10.0.0.1:8080';
        console.log('[Config] Ambiente CORE detectado →', coreApi);
        return coreApi;
    }

    // Ambiente local (fallback)
    const local = 'http://localhost:8080';
    console.log('[Config] Ambiente local →', local);
    return local;
}


// ===================== CONFIG GLOBAL =====================

const API_BASE = detectApiBase();
const AUTO_REFRESH_INTERVAL = 5000;
const TELEMETRY_MAX_ITEMS = 20;
const ACTIVITY_MAX_ITEMS = 50;

console.log('╔═══════════════════════════════════════════════╗');
console.log('║       Ground Control Station - Config         ║');
console.log('╠═══════════════════════════════════════════════╣');
console.log('  Ambiente  →', window.location.hostname.startsWith('10.0.') ? 'CORE-EMU' : 'Local/Browser');
console.log('  API Base  →', API_BASE);
console.log('  Hostname  →', window.location.hostname);
console.log('  Página    →', window.location.href);
console.log('╚═══════════════════════════════════════════════╝');


// ===================== STATE =====================

let autoRefreshInterval = null;
let currentFilters = { rovers: 'all', missions: 'all' };
let activityLog = [];
let previousData = { rovers: [], missions: [] };


// ===================== INIT =====================

document.addEventListener('DOMContentLoaded', () => {
    console.log('[Ground Control] Init');
    checkConnection();
    loadAllData();
    startAutoRefresh();
    setupListeners();
    setupFilters();
    addActivity('info', 'Sistema iniciado');
});


// ===================== HTTP WRAPPER =====================

async function apiGet(path) {
    return httpWrapper(() => fetch(`${API_BASE}${path}`));
}

async function apiPost(path, body) {
    return httpWrapper(() => fetch(`${API_BASE}${path}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    }));
}

/**
 * Função reutilizada para GET/POST
 */
async function httpWrapper(requestFn) {
    try {
        const res = await requestFn();
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        updateConnectionStatus(true);
        return await res.json();
    } catch (err) {
        updateConnectionStatus(false);
        throw err;
    }
}


// ===================== LISTENERS =====================

function setupListeners() {
    const autoScrollToggle = document.getElementById('auto-scroll-toggle');
    if (autoScrollToggle) {
        autoScrollToggle.addEventListener('change', e =>
            console.log('[UI] Auto-scroll:', e.target.checked)
        );
    }

    window.addEventListener('click', e => {
        if (e.target.classList.contains('modal')) closeAllModals();
    });

    document.addEventListener('keydown', e => {
        if (e.key === 'Escape') closeAllModals();
    });
}

function setupFilters() {
    document.querySelectorAll('.filter-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const group = btn.parentElement;
            group.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            const section = btn.closest('.section');
            const type = section.dataset.type;

            currentFilters[type] = btn.dataset.filter;

            if (type === 'rovers') filterRovers();
            else filterMissions();
        });
    });
}


// ===================== CONNECTION =====================

async function checkConnection() {
    try {
        await apiGet('/rovers');
        addActivity('success', 'Conexão estabelecida');
    } catch {
        addActivity('error', 'Falha na conexão');
    }
}

function updateConnectionStatus(online) {
    const statusEl = document.getElementById('connection-status');
    const textEl = document.getElementById('status-text');

    statusEl.classList.toggle('online', online);
    statusEl.classList.toggle('offline', !online);
    textEl.textContent = online ? 'Conectado' : 'Desconectado';
}


// ===================== DATA LOADING =====================

async function loadAllData() {
    try {
        const [rovers, missions, telemetry] = await Promise.all([
            apiGet('/rovers'),
            apiGet('/missoes'),
            apiGet('/telemetria/historico')
        ]);

        detectRoverChanges(rovers);
        detectMissionChanges(missions);

        previousData = { rovers, missions };

        renderRovers(rovers);
        renderMissions(missions);
        renderTelemetry(telemetry);
        renderMissionTimeline();

        updateStats();
        updateLastUpdateTime();
    } catch (err) {
        console.error('[loadAllData]', err);
    }
}


// ===================== CHANGE DETECTION =====================

function detectRoverChanges(list) {
    if (!previousData.rovers.length) return;

    list.forEach(r => {
        const old = previousData.rovers.find(o => o.idRover === r.idRover);
        if (!old) return addActivity('success', `Novo rover: ${r.idRover}`);

        if (old.estadoOperacional !== r.estadoOperacional)
            addActivity('info', `Rover ${r.idRover}: ${old.estadoOperacional} → ${r.estadoOperacional}`);

        if (!old.temMissao && r.temMissao)
            addActivity('success', `Rover ${r.idRover} iniciou missão`);

        if (old.temMissao && !r.temMissao)
            addActivity('success', `Rover ${r.idRover} concluiu missão`);

        if (r.bateria < 20 && old.bateria >= 20)
            addActivity('warning', `Rover ${r.idRover} bateria crítica`);
    });
}

function detectMissionChanges(list) {
    if (!previousData.missions.length) return;

    list.forEach(m => {
        const old = previousData.missions.find(o => o.idMissao === m.idMissao);
        if (!old) return addActivity('info', `Nova missão: #${m.idMissao}`);

        if (old.estado !== m.estado)
            addActivity('info', `Missão #${m.idMissao}: ${old.estado} → ${m.estado}`);
    });
}


// ===================== RENDER HELPERS =====================

const empty = msg => `<div class="empty-state">${msg}</div>`;

function normalizeEstado(s) {
    const st = s.toLowerCase().replace(/estado_/g, '');
    
    // Manter underscores para matching com data-filter
    if (st.includes('dispon')) return 'disponivel';
    if (st.includes('exec') || st.includes('miss')) return 'em-missao';
    if (st.includes('andamento') || st === 'em_andamento') return 'em_andamento';
    if (st.includes('pend')) return 'pendente';
    if (st.includes('concl')) return 'concluida';
    if (st.includes('erro') || st.includes('falha')) return 'erro';
    
    return st;
}

function renderList(containerId, list, renderFn, emptyMsg, filterFn) {
    const container = document.getElementById(containerId);
    if (!list.length) return container.innerHTML = empty(emptyMsg);

    container.innerHTML = list.map(renderFn).join('');
    if (filterFn) filterFn();
}


// ===================== RENDERING =====================

function renderRovers(rovers) {
    renderList('rovers-container', rovers, createRoverCard, 'Nenhum rover', filterRovers);
}

function createRoverCard(rover) {
    const estado = normalizeEstado(rover.estadoOperacional);
    const batteryClass = rover.bateria < 20 ? 'danger' : rover.bateria < 50 ? 'warning' : '';

    return `
        <div class="rover-card" data-estado="${estado}" onclick="showRoverDetails(${rover.idRover})">
            <div class="rover-header">
                <span class="rover-id">Rover ${rover.idRover}</span>
                <span class="estado ${estado}">${rover.estadoOperacional}</span>
            </div>
            <div class="rover-body">
                <div class="info-item"><span class="info-label">Posição</span>
                    <span class="info-value">(${rover.posicaoX.toFixed(1)}, ${rover.posicaoY.toFixed(1)})</span>
                </div>
                <div class="info-item"><span class="info-label">Bateria</span>
                    <span class="info-value ${batteryClass}">${rover.bateria}%</span>
                </div>
                <div class="info-item"><span class="info-label">Velocidade</span>
                    <span class="info-value">${rover.velocidade.toFixed(2)} m/s</span>
                </div>
                ${rover.temMissao ? renderRoverMission(rover) : ''}
            </div>
        </div>
    `;
}

function renderRoverMission(rover) {
    return `
        <div class="info-item">
            <span class="info-label">Missão</span>
            <span class="info-value">#${rover.idMissaoAtual}</span>
        </div>
        <div class="progress-section">
            <div class="progress-header">
                <span class="info-label">Progresso</span>
                <span class="info-value">${rover.progressoMissao.toFixed(1)}%</span>
            </div>
            <div class="progress-bar">
                <div class="progress-fill" style="width: ${rover.progressoMissao}%"></div>
            </div>
        </div>
    `;
}

function renderMissions(missions) {
    renderList('missions-container', missions, createMissionCard, 'Nenhuma missão', filterMissions);
}

function createMissionCard(m) {
    const estado = normalizeEstado(m.estado);
    const priority = getPriorityClass(m.prioridade);
    const dist = calculateDistance(m.x1, m.y1, m.x2, m.y2);

    return `
        <div class="mission-card" data-estado="${estado}" onclick="showMissionDetails(${m.idMissao})">
            <div class="mission-header">
                <span class="mission-id">Missão #${m.idMissao}</span>
                <span class="estado ${estado}">${m.estado}</span>
            </div>
            <div class="mission-body">
                <div class="mission-task">${m.tarefa}</div>
                <div class="info-item"><span class="info-label">Prioridade</span>
                    <span class="priority ${priority}">${getPriorityLabel(m.prioridade)}</span>
                </div>
                <div class="info-item"><span class="info-label">Distância</span>
                    <span class="info-value">${dist.toFixed(1)} unidades</span>
                </div>
                <div class="info-item"><span class="info-label">Início</span>
                    <span class="info-value">(${m.x1.toFixed(1)}, ${m.y1.toFixed(1)})</span>
                </div>
                <div class="info-item"><span class="info-label">Fim</span>
                    <span class="info-value">(${m.x2.toFixed(1)}, ${m.y2.toFixed(1)})</span>
                </div>
            </div>
        </div>
    `;
}

function renderTelemetry(list) {
    renderList(
        'telemetry-stream',
        list.slice(-TELEMETRY_MAX_ITEMS),
        renderTelemetryItem,
        'Sem telemetria'
    );
}

function renderTelemetryItem(data, index, arr) {
    const estado = normalizeEstado(data.estadoOperacional);
    const timestamp = arr.length - (TELEMETRY_MAX_ITEMS - index - 1);

    return `
        <div class="telemetry-item">
            <div class="info-item">
                <span class="info-label">Timestamp</span>
                <span class="info-value">#${timestamp}</span>
            </div>
            <div class="info-item">
                <span class="info-label">Posição</span>
                <span class="info-value">(${data.posicaoX.toFixed(2)}, ${data.posicaoY.toFixed(2)})</span>
            </div>
            <div class="info-item">
                <span class="info-label">Estado</span>
                <span class="estado ${estado}">${data.estadoOperacional}</span>
            </div>
            <div class="info-item">
                <span class="info-label">Bateria</span>
                <span class="info-value">${data.bateria}%</span>
            </div>
            <div class="info-item">
                <span class="info-label">Velocidade</span>
                <span class="info-value">${data.velocidade.toFixed(2)} m/s</span>
            </div>
        </div>
    `;
}


function renderMissionTimeline() {
    const container = document.getElementById('mission-timeline');
    const rovers = previousData.rovers.filter(r => r.temMissao);

    if (!rovers.length) {
        return container.innerHTML = empty('Nenhuma missão ativa');
    }

    container.innerHTML = rovers.map(renderTimelineItem).join('');
}

function renderTimelineItem(rover) {
    const mission = previousData.missions.find(m => m.idMissao === rover.idMissaoAtual);
    if (!mission) return '';

    const progress = rover.progressoMissao;
    const progressClass = progress < 30 ? 'danger' : progress < 70 ? 'warning' : '';
    const priority = getPriorityClass(mission.prioridade);

    return `
        <div class="timeline-item">
            <div class="timeline-header">
                <div class="timeline-mission-info">
                    <span class="timeline-mission-id">Missão #${mission.idMissao}</span>
                    <span class="timeline-rover-badge">Rover ${rover.idRover}</span>
                    <span class="priority ${priority}">${getPriorityLabel(mission.prioridade)}</span>
                </div>
                <span class="estado em_andamento">EM ANDAMENTO</span>
            </div>
            <div class="timeline-progress">
                <div class="timeline-progress-bar">
                    <div class="timeline-progress-fill ${progressClass}" style="width: ${progress}%"></div>
                    <div class="timeline-progress-label">${progress.toFixed(1)}%</div>
                </div>
            </div>
            <div class="timeline-details">
                <div class="info-item"><span class="info-label">Tarefa</span>
                    <span class="info-value">${mission.tarefa}</span></div>
                <div class="info-item"><span class="info-label">Posição Rover</span>
                    <span class="info-value">(${rover.posicaoX.toFixed(1)}, ${rover.posicaoY.toFixed(1)})</span></div>
                <div class="info-item"><span class="info-label">Bateria</span>
                    <span class="info-value">${rover.bateria}%</span></div>
                <div class="info-item"><span class="info-label">Velocidade</span>
                    <span class="info-value">${rover.velocidade.toFixed(2)} m/s</span></div>
            </div>
        </div>
    `;
}


// ===================== UTILITIES =====================

function calculateDistance(x1, y1, x2, y2) {
    return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
}

function getPriorityClass(p) {
    if (p >= 3) return 'alta';
    if (p >= 2) return 'normal';
    return 'baixa';
}

function getPriorityLabel(p) {
    if (p >= 3) return 'Alta';
    if (p >= 2) return 'Normal';
    return 'Baixa';
}

function updateStats() {
    const rovers = previousData.rovers;
    const missions = previousData.missions;

    document.getElementById('total-rovers').textContent = rovers.length;
    document.getElementById('rovers-disponivel').textContent = 
        rovers.filter(r => normalizeEstado(r.estadoOperacional) === 'disponivel').length;
    
    document.getElementById('missoes-ativas').textContent = 
        missions.filter(m => ['em_execucao', 'em_andamento'].includes(normalizeEstado(m.estado))).length;
    
    document.getElementById('missoes-concluidas').textContent = 
        missions.filter(m => normalizeEstado(m.estado) === 'concluida').length;

    const roversComMissao = rovers.filter(r => r.temMissao);
    const progressoMedio = roversComMissao.length 
        ? roversComMissao.reduce((sum, r) => sum + r.progressoMissao, 0) / roversComMissao.length 
        : 0;
    document.getElementById('progresso-medio').textContent = progressoMedio.toFixed(0) + '%';

    const bateriaMedia = rovers.length 
        ? rovers.reduce((sum, r) => sum + r.bateria, 0) / rovers.length 
        : 0;
    document.getElementById('bateria-media').textContent = bateriaMedia.toFixed(0) + '%';
}

function updateLastUpdateTime() {
    document.getElementById('last-update').textContent =
        new Date().toLocaleTimeString('pt-PT');
}

function startAutoRefresh() {
    clearInterval(autoRefreshInterval);
    autoRefreshInterval = setInterval(loadAllData, AUTO_REFRESH_INTERVAL);
}

function closeAllModals() {
    document.querySelectorAll('.modal').forEach(m => m.classList.remove('show'));
}


// ===================== FILTERS =====================

function filterRovers() {
    const filter = currentFilters.rovers;
    document.querySelectorAll('.rover-card').forEach(card => {
        const estado = card.dataset.estado;
        card.classList.toggle('hidden', filter !== 'all' && estado !== filter);
    });
}

function filterMissions() {
    const filter = currentFilters.missions;
    document.querySelectorAll('.mission-card').forEach(card => {
        const estado = card.dataset.estado;
        card.classList.toggle('hidden', filter !== 'all' && estado !== filter);
    });
}


// ===================== ACTIVITY LOG =====================

function addActivity(type, message) {
    const time = new Date().toLocaleTimeString('pt-PT');
    activityLog.unshift({ type, message, time });
    
    if (activityLog.length > ACTIVITY_MAX_ITEMS) {
        activityLog = activityLog.slice(0, ACTIVITY_MAX_ITEMS);
    }
    
    renderActivityLog();
}

function renderActivityLog() {
    const container = document.getElementById('activity-log');
    if (!activityLog.length) {
        container.innerHTML = '<div class="activity-item info"><span class="activity-time">--:--:--</span><span class="activity-message">Sem atividade</span></div>';
        return;
    }
    
    container.innerHTML = activityLog.map(a => `
        <div class="activity-item ${a.type}">
            <span class="activity-time">${a.time}</span>
            <span class="activity-message">${a.message}</span>
        </div>
    `).join('');
}

function clearActivityLog() {
    activityLog = [];
    renderActivityLog();
}


// ===================== MODALS =====================

function showCreateMissionModal() {
    document.getElementById('mission-modal').classList.add('show');
}

function closeCreateMissionModal() {
    document.getElementById('mission-modal').classList.remove('show');
    document.getElementById('mission-form').reset();
}

async function createMission(event) {
    event.preventDefault();
    
    const missao = {
        idMissao: parseInt(document.getElementById('mission-id').value),
        tarefa: document.getElementById('mission-task').value,
        estado: 'PENDENTE',
        prioridade: parseInt(document.getElementById('mission-priority').value),
        x1: parseFloat(document.getElementById('mission-x1').value),
        y1: parseFloat(document.getElementById('mission-y1').value),
        x2: parseFloat(document.getElementById('mission-x2').value),
        y2: parseFloat(document.getElementById('mission-y2').value)
    };
    
    try {
        await apiPost('/missoes', missao);
        addActivity('success', `Missão #${missao.idMissao} criada com sucesso`);
        closeCreateMissionModal();
        loadAllData();
    } catch (err) {
        addActivity('error', `Erro ao criar missão: ${err.message}`);
        console.error(err);
    }
}

function showRoverDetails(id) {
    const modal = document.getElementById('rover-modal');
    const title = document.getElementById('rover-modal-title');
    const body = document.getElementById('rover-modal-body');
    
    const rover = previousData.rovers.find(r => r.idRover === id);
    if (!rover) return;
    
    title.textContent = `Rover #${rover.idRover}`;
    
    body.innerHTML = `
        <div class="detail-grid">
            <div class="detail-item">
                <div class="detail-label">Estado</div>
                <div class="detail-value"><span class="estado ${normalizeEstado(rover.estadoOperacional)}">${rover.estadoOperacional}</span></div>
            </div>
            <div class="detail-item">
                <div class="detail-label">Bateria</div>
                <div class="detail-value">${rover.bateria}%</div>
            </div>
            <div class="detail-item">
                <div class="detail-label">Posição X</div>
                <div class="detail-value">${rover.posicaoX.toFixed(2)}</div>
            </div>
            <div class="detail-item">
                <div class="detail-label">Posição Y</div>
                <div class="detail-value">${rover.posicaoY.toFixed(2)}</div>
            </div>
            <div class="detail-item">
                <div class="detail-label">Velocidade</div>
                <div class="detail-value">${rover.velocidade.toFixed(2)} m/s</div>
            </div>
            <div class="detail-item">
                <div class="detail-label">Tem Missão</div>
                <div class="detail-value">${rover.temMissao ? 'Sim' : 'Não'}</div>
            </div>
            ${rover.temMissao ? `
                <div class="detail-item">
                    <div class="detail-label">ID Missão</div>
                    <div class="detail-value">#${rover.idMissaoAtual}</div>
                </div>
                <div class="detail-item">
                    <div class="detail-label">Progresso</div>
                    <div class="detail-value">${rover.progressoMissao.toFixed(1)}%</div>
                </div>
            ` : ''}
        </div>
    `;
    
    modal.classList.add('show');
}

function closeRoverModal() {
    document.getElementById('rover-modal').classList.remove('show');
}

function showMissionDetails(id) {
    const modal = document.getElementById('mission-detail-modal');
    const title = document.getElementById('mission-modal-title');
    const body = document.getElementById('mission-modal-body');
    
    const mission = previousData.missions.find(m => m.idMissao === id);
    if (!mission) return;
    
    title.textContent = `Missão #${mission.idMissao}`;
    
    const dist = calculateDistance(mission.x1, mission.y1, mission.x2, mission.y2);
    
    body.innerHTML = `
        <div class="detail-grid">
            <div class="detail-item detail-full">
                <div class="detail-label">Tarefa</div>
                <div class="detail-value">${mission.tarefa}</div>
            </div>
            <div class="detail-item">
                <div class="detail-label">Estado</div>
                <div class="detail-value"><span class="estado ${normalizeEstado(mission.estado)}">${mission.estado}</span></div>
            </div>
            <div class="detail-item">
                <div class="detail-label">Prioridade</div>
                <div class="detail-value"><span class="priority ${getPriorityClass(mission.prioridade)}">${getPriorityLabel(mission.prioridade)}</span></div>
            </div>
            <div class="detail-item">
                <div class="detail-label">Ponto Inicial</div>
                <div class="detail-value">(${mission.x1.toFixed(2)}, ${mission.y1.toFixed(2)})</div>
            </div>
            <div class="detail-item">
                <div class="detail-label">Ponto Final</div>
                <div class="detail-value">(${mission.x2.toFixed(2)}, ${mission.y2.toFixed(2)})</div>
            </div>
            <div class="detail-item">
                <div class="detail-label">Distância</div>
                <div class="detail-value">${dist.toFixed(2)} unidades</div>
            </div>
        </div>
    `;
    
    modal.classList.add('show');
}

function closeMissionModal() {
    document.getElementById('mission-detail-modal').classList.remove('show');
}


// ===================== REFRESH FUNCTIONS =====================

function refreshRovers() {
    addActivity('info', 'Atualizando rovers...');
    loadAllData();
}

function refreshMissions() {
    addActivity('info', 'Atualizando missões...');
    loadAllData();
}

function refreshTelemetry() {
    addActivity('info', 'Atualizando telemetria...');
    loadAllData();
}