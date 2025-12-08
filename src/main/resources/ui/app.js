// ========== Configuration ==========
const API_BASE = 'http://localhost:8080';
const AUTO_REFRESH_INTERVAL = 5000; // 5 seconds
const TELEMETRY_MAX_ITEMS = 20;
const ACTIVITY_MAX_ITEMS = 50;

// ========== State Management ==========
let autoRefreshInterval = null;
let currentFilters = {
    rovers: 'all',
    missions: 'all'
};
let activityLog = [];
let previousData = {
    rovers: [],
    missions: []
};

// ========== Initialization ==========
document.addEventListener('DOMContentLoaded', () => {
    console.log('[Ground Control] ============================================');
    console.log('[Ground Control] Initializing Dashboard...');
    console.log('[Ground Control] API Base:', API_BASE);
    console.log('[Ground Control] Current URL:', window.location.href);
    console.log('[Ground Control] ============================================');
    
    // Test if DOM elements exist
    const requiredElements = [
        'rovers-container',
        'missions-container', 
        'telemetry-stream',
        'activity-log',
        'connection-status'
    ];
    
    requiredElements.forEach(id => {
        const el = document.getElementById(id);
        console.log(`[DOM Check] Element #${id}:`, el ? '✓ Found' : '✗ NOT FOUND');
    });
    
    checkConnection();
    loadAllData();
    startAutoRefresh();
    setupEventListeners();
    setupFilters();
    
    addActivity('info', 'Sistema Ground Control iniciado');
    console.log('[Ground Control] Ready');
    console.log('[Ground Control] ============================================');
});

// ========== Event Listeners ==========
function setupEventListeners() {
    // Auto-scroll toggle
    const autoScrollToggle = document.getElementById('auto-scroll-toggle');
    if (autoScrollToggle) {
        autoScrollToggle.addEventListener('change', (e) => {
            console.log('[UI] Auto-scroll:', e.target.checked);
        });
    }
}

function setupFilters() {
    // Rover filters
    document.querySelectorAll('.section:nth-child(1) .filter-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            btn.parentElement.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            currentFilters.rovers = btn.dataset.filter;
            filterRovers();
        });
    });
    
    // Mission filters
    document.querySelectorAll('.section:nth-child(2) .filter-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            btn.parentElement.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            currentFilters.missions = btn.dataset.filter;
            filterMissions();
        });
    });
}

// ========== Connection Check ==========
async function checkConnection() {
    try {
        console.log('[Connection] Testing connection to:', API_BASE);
        const response = await fetch(`${API_BASE}/rovers`, {
            method: 'GET',
            headers: {
                'Accept': 'application/json'
            }
        });
        console.log('[Connection] Response received:', response.status, response.statusText);
        updateConnectionStatus(response.ok);
        
        if (response.ok) {
            addActivity('success', 'Conexão estabelecida com a API');
        } else {
            addActivity('error', `Erro na conexão: HTTP ${response.status}`);
        }
    } catch (error) {
        console.error('[Connection] Failed:', error);
        console.error('[Connection] Error details:', {
            message: error.message,
            name: error.name,
            stack: error.stack
        });
        updateConnectionStatus(false);
        addActivity('error', 'Falha na conexão: ' + error.message);
    }
}

function updateConnectionStatus(isOnline) {
    const statusEl = document.getElementById('connection-status');
    const textEl = document.getElementById('status-text');
    
    if (isOnline) {
        statusEl.classList.add('online');
        statusEl.classList.remove('offline');
        textEl.textContent = 'Conectado';
    } else {
        statusEl.classList.add('offline');
        statusEl.classList.remove('online');
        textEl.textContent = 'Desconectado';
    }
}

// ========== Data Loading ==========
async function loadAllData() {
    await Promise.all([
        refreshRovers(),
        refreshMissions(),
        refreshTelemetry()
    ]);
    updateLastUpdateTime();
    updateStats();
    renderMissionTimeline();
}

async function refreshRovers() {
    try {
        console.log('[API] Fetching rovers from:', `${API_BASE}/rovers`);
        const response = await fetch(`${API_BASE}/rovers`);
        
        console.log('[API] Response status:', response.status);
        console.log('[API] Response headers:', response.headers);
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        
        const text = await response.text();
        console.log('[API] Raw response:', text);
        
        let rovers;
        try {
            rovers = JSON.parse(text);
        } catch (parseError) {
            console.error('[API] JSON parse error:', parseError);
            throw new Error('Resposta inválida da API');
        }
        
        console.log('[API] Rovers loaded:', rovers);
        console.log('[API] Number of rovers:', Array.isArray(rovers) ? rovers.length : 'not an array');
        
        // Detect changes
        detectRoverChanges(rovers);
        
        previousData.rovers = Array.isArray(rovers) ? rovers : [];
        renderRovers(previousData.rovers);
        updateConnectionStatus(true);
        
        addActivity('success', `${previousData.rovers.length} rovers carregados`);
    } catch (error) {
        console.error('[API] Error loading rovers:', error);
        addActivity('error', 'Erro ao carregar rovers: ' + error.message);
        showError('rovers-container', 'Erro ao carregar rovers: ' + error.message);
        updateConnectionStatus(false);
    }
}

async function refreshMissions() {
    try {
        console.log('[API] Fetching missions from:', `${API_BASE}/missoes`);
        const response = await fetch(`${API_BASE}/missoes`);
        
        console.log('[API] Response status:', response.status);
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        
        const text = await response.text();
        console.log('[API] Raw response:', text);
        
        let missions;
        try {
            missions = JSON.parse(text);
        } catch (parseError) {
            console.error('[API] JSON parse error:', parseError);
            throw new Error('Resposta inválida da API');
        }
        
        console.log('[API] Missions loaded:', missions);
        console.log('[API] Number of missions:', Array.isArray(missions) ? missions.length : 'not an array');
        
        // Detect changes
        detectMissionChanges(missions);
        
        previousData.missions = Array.isArray(missions) ? missions : [];
        renderMissions(previousData.missions);
        updateConnectionStatus(true);
        
        addActivity('success', `${previousData.missions.length} missões carregadas`);
    } catch (error) {
        console.error('[API] Error loading missions:', error);
        addActivity('error', 'Erro ao carregar missões: ' + error.message);
        showError('missions-container', 'Erro ao carregar missões: ' + error.message);
        updateConnectionStatus(false);
    }
}

async function refreshTelemetry() {
    try {
        console.log('[API] Fetching telemetry...');
        const response = await fetch(`${API_BASE}/telemetria/historico`);
        const telemetry = await response.json();
        
        console.log('[API] Telemetry loaded:', telemetry.length);
        renderTelemetry(telemetry);
        updateConnectionStatus(true);
    } catch (error) {
        console.error('[API] Error loading telemetry:', error);
        showError('telemetry-stream', 'Erro ao carregar telemetria');
        updateConnectionStatus(false);
    }
}

// ========== Change Detection ==========
function detectRoverChanges(newRovers) {
    if (previousData.rovers.length === 0) return;
    
    newRovers.forEach(rover => {
        const oldRover = previousData.rovers.find(r => r.idRover === rover.idRover);
        
        if (!oldRover) {
            addActivity('success', `Novo rover detectado: Rover ${rover.idRover}`);
        } else {
            // Estado mudou
            if (oldRover.estadoOperacional !== rover.estadoOperacional) {
                addActivity('info', `Rover ${rover.idRover}: ${oldRover.estadoOperacional} → ${rover.estadoOperacional}`);
            }
            
            // Missão atribuída
            if (!oldRover.temMissao && rover.temMissao) {
                addActivity('success', `Rover ${rover.idRover} iniciou missão #${rover.idMissaoAtual}`);
            }
            
            // Missão concluída
            if (oldRover.temMissao && !rover.temMissao) {
                addActivity('success', `Rover ${rover.idRover} concluiu missão #${oldRover.idMissaoAtual}`);
            }
            
            // Bateria crítica
            if (rover.bateria < 20 && oldRover.bateria >= 20) {
                addActivity('warning', `Rover ${rover.idRover}: bateria crítica (${rover.bateria}%)`);
            }
        }
    });
}

function detectMissionChanges(newMissions) {
    if (previousData.missions.length === 0) return;
    
    newMissions.forEach(mission => {
        const oldMission = previousData.missions.find(m => m.idMissao === mission.idMissao);
        
        if (!oldMission) {
            addActivity('info', `Nova missão: #${mission.idMissao} - ${mission.tarefa}`);
        } else {
            // Estado mudou
            if (oldMission.estado !== mission.estado) {
                addActivity('info', `Missão #${mission.idMissao}: ${oldMission.estado} → ${mission.estado}`);
            }
        }
    });
}

// ========== Rendering Functions ==========
function renderRovers(rovers) {
    const container = document.getElementById('rovers-container');
    
    console.log('[Render] Rendering rovers:', rovers);
    console.log('[Render] Is array?', Array.isArray(rovers));
    console.log('[Render] Length:', rovers ? rovers.length : 'null/undefined');
    
    if (!rovers || !Array.isArray(rovers) || rovers.length === 0) {
        container.innerHTML = '<div class="empty-state">Nenhum rover registrado</div>';
        console.log('[Render] No rovers to display');
        return;
    }
    
    const html = rovers.map(rover => createRoverCard(rover)).join('');
    console.log('[Render] Generated HTML length:', html.length);
    
    container.innerHTML = html;
    filterRovers();
    
    console.log('[Render] Rovers rendered successfully');
}

function createRoverCard(rover) {
    const hasMission = rover.temMissao;
    const estado = normalizeEstado(rover.estadoOperacional);
    const batteryClass = rover.bateria < 20 ? 'danger' : rover.bateria < 50 ? 'warning' : '';
    
    return `
        <div class="rover-card" data-estado="${estado}">
            <div class="rover-header">
                <span class="rover-id">Rover ${rover.idRover}</span>
                <span class="estado ${estado}">${rover.estadoOperacional}</span>
            </div>
            <div class="rover-body">
                <div class="info-item">
                    <span class="info-label">Posição</span>
                    <span class="info-value">(${rover.posicaoX.toFixed(1)}, ${rover.posicaoY.toFixed(1)})</span>
                </div>
                <div class="info-item">
                    <span class="info-label">Bateria</span>
                    <span class="info-value ${batteryClass}">${rover.bateria}%</span>
                </div>
                <div class="info-item">
                    <span class="info-label">Velocidade</span>
                    <span class="info-value">${rover.velocidade.toFixed(2)} m/s</span>
                </div>
                ${hasMission ? `
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
                ` : ''}
            </div>
        </div>
    `;
}

function renderMissions(missions) {
    const container = document.getElementById('missions-container');
    
    console.log('[Render] Rendering missions:', missions);
    console.log('[Render] Is array?', Array.isArray(missions));
    console.log('[Render] Length:', missions ? missions.length : 'null/undefined');
    
    if (!missions || !Array.isArray(missions) || missions.length === 0) {
        container.innerHTML = '<div class="empty-state">Nenhuma missão registrada</div>';
        console.log('[Render] No missions to display');
        return;
    }
    
    const html = missions.map(mission => createMissionCard(mission)).join('');
    console.log('[Render] Generated HTML length:', html.length);
    
    container.innerHTML = html;
    filterMissions();
    
    console.log('[Render] Missions rendered successfully');
}

function createMissionCard(mission) {
    const estado = normalizeEstado(mission.estado);
    const priority = getPriorityClass(mission.prioridade);
    const distance = calculateDistance(mission.x1, mission.y1, mission.x2, mission.y2);
    
    return `
        <div class="mission-card" data-estado="${estado}">
            <div class="mission-header">
                <span class="mission-id">Missão #${mission.idMissao}</span>
                <span class="estado ${estado}">${mission.estado}</span>
            </div>
            <div class="mission-body">
                <div class="mission-task">${mission.tarefa}</div>
                <div class="info-item">
                    <span class="info-label">Prioridade</span>
                    <span class="priority ${priority}">${getPriorityLabel(mission.prioridade)}</span>
                </div>
                <div class="info-item">
                    <span class="info-label">Distância</span>
                    <span class="info-value">${distance.toFixed(1)} unidades</span>
                </div>
                <div class="info-item">
                    <span class="info-label">Início</span>
                    <span class="info-value">(${mission.x1.toFixed(1)}, ${mission.y1.toFixed(1)})</span>
                </div>
                <div class="info-item">
                    <span class="info-label">Fim</span>
                    <span class="info-value">(${mission.x2.toFixed(1)}, ${mission.y2.toFixed(1)})</span>
                </div>
            </div>
        </div>
    `;
}

function renderMissionTimeline() {
    const container = document.getElementById('mission-timeline');
    const rovers = previousData.rovers.filter(r => r.temMissao);
    
    if (!rovers || rovers.length === 0) {
        container.innerHTML = '<div class="empty-state">Nenhuma missão ativa no momento</div>';
        return;
    }
    
    container.innerHTML = rovers.map(rover => {
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
                    <span class="estado em_execucao">EM EXECUÇÃO</span>
                </div>
                <div class="timeline-progress">
                    <div class="timeline-progress-bar">
                        <div class="timeline-progress-fill ${progressClass}" style="width: ${progress}%">
                        </div>
                        <div class="timeline-progress-label">${progress.toFixed(1)}%</div>
                    </div>
                </div>
                <div class="timeline-details">
                    <div class="info-item">
                        <span class="info-label">Tarefa</span>
                        <span class="info-value">${mission.tarefa}</span>
                    </div>
                    <div class="info-item">
                        <span class="info-label">Posição Rover</span>
                        <span class="info-value">(${rover.posicaoX.toFixed(1)}, ${rover.posicaoY.toFixed(1)})</span>
                    </div>
                    <div class="info-item">
                        <span class="info-label">Bateria</span>
                        <span class="info-value">${rover.bateria}%</span>
                    </div>
                    <div class="info-item">
                        <span class="info-label">Velocidade</span>
                        <span class="info-value">${rover.velocidade.toFixed(2)} m/s</span>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

function renderTelemetry(telemetry) {
    const container = document.getElementById('telemetry-stream');
    const autoScroll = document.getElementById('auto-scroll-toggle').checked;
    
    if (!telemetry || telemetry.length === 0) {
        container.innerHTML = '<div class="empty-state">Nenhum dado de telemetria</div>';
        return;
    }
    
    const recent = telemetry.slice(-TELEMETRY_MAX_ITEMS);
    
    container.innerHTML = recent.map((data, index) => {
        const estado = normalizeEstado(data.estadoOperacional);
        return `
            <div class="telemetry-item">
                <div class="info-item">
                    <span class="info-label">Timestamp</span>
                    <span class="info-value">#${telemetry.length - (TELEMETRY_MAX_ITEMS - index - 1)}</span>
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
    }).join('');
    
    if (autoScroll) {
        container.scrollTop = 0;
    }
}

// ========== Activity Log ==========
function addActivity(type, message) {
    const now = new Date();
    const time = now.toLocaleTimeString('pt-PT');
    
    activityLog.unshift({ type, message, time });
    
    // Limit log size
    if (activityLog.length > ACTIVITY_MAX_ITEMS) {
        activityLog.pop();
    }
    
    renderActivityLog();
}

function renderActivityLog() {
    const container = document.getElementById('activity-log');
    
    container.innerHTML = activityLog.map(activity => `
        <div class="activity-item ${activity.type}">
            <span class="activity-time">${activity.time}</span>
            <span class="activity-message">${activity.message}</span>
        </div>
    `).join('');
}

function clearActivityLog() {
    activityLog = [];
    addActivity('info', 'Log de atividades limpo');
}

// ========== Filtering ==========
function filterRovers() {
    const filter = currentFilters.rovers;
    const cards = document.querySelectorAll('.rover-card');
    
    cards.forEach(card => {
        if (filter === 'all') {
            card.classList.remove('hidden');
        } else {
            const estado = card.dataset.estado;
            card.classList.toggle('hidden', estado !== filter);
        }
    });
}

function filterMissions() {
    const filter = currentFilters.missions;
    const cards = document.querySelectorAll('.mission-card');
    
    cards.forEach(card => {
        if (filter === 'all') {
            card.classList.remove('hidden');
        } else {
            const estado = card.dataset.estado;
            card.classList.toggle('hidden', estado !== filter);
        }
    });
}

// ========== Stats Update ==========
function updateStats() {
    const rovers = previousData.rovers;
    const missions = previousData.missions;
    
    let totalRovers = rovers.length;
    let roversDisponivel = 0;
    let missoesAtivas = 0;
    let missoesConcluidas = 0;
    let totalProgress = 0;
    let roversComMissao = 0;
    let totalBattery = 0;
    
    rovers.forEach(rover => {
        if (normalizeEstado(rover.estadoOperacional) === 'disponivel') roversDisponivel++;
        if (rover.temMissao) {
            roversComMissao++;
            totalProgress += rover.progressoMissao;
        }
        totalBattery += rover.bateria;
    });
    
    missions.forEach(mission => {
        const estado = normalizeEstado(mission.estado);
        if (estado === 'em-missao' || estado === 'em_execucao' || estado === 'pendente') missoesAtivas++;
        if (estado === 'concluida') missoesConcluidas++;
    });
    
    const progressoMedio = roversComMissao > 0 ? (totalProgress / roversComMissao).toFixed(0) : 0;
    const bateriMedia = totalRovers > 0 ? (totalBattery / totalRovers).toFixed(0) : 0;
    
    document.getElementById('total-rovers').textContent = totalRovers;
    document.getElementById('rovers-disponivel').textContent = roversDisponivel;
    document.getElementById('missoes-ativas').textContent = missoesAtivas;
    document.getElementById('missoes-concluidas').textContent = missoesConcluidas;
    document.getElementById('progresso-medio').textContent = progressoMedio + '%';
    document.getElementById('bateria-media').textContent = bateriMedia + '%';
}

// ========== Auto Refresh ==========
function startAutoRefresh() {
    if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval);
    }
    
    autoRefreshInterval = setInterval(() => {
        console.log('[Auto-Refresh] Updating data...');
        loadAllData();
    }, AUTO_REFRESH_INTERVAL);
    
    console.log('[Auto-Refresh] Started (interval: ' + AUTO_REFRESH_INTERVAL + 'ms)');
}

// ========== Utility Functions ==========
function updateLastUpdateTime() {
    const now = new Date();
    document.getElementById('last-update').textContent = now.toLocaleTimeString('pt-PT');
}

function normalizeEstado(estado) {
    // Remover "ESTADO_" do início se existir
    const normalized = estado.toLowerCase()
        .replace('estado_', '')
        .replace(/_/g, '-')
        .replace(/\s+/g, '-');
    
    if (normalized.includes('disponivel') || normalized.includes('inicial')) return 'disponivel';
    if (normalized.includes('missao') || normalized.includes('execucao') || normalized.includes('andamento')) return 'em-missao';
    if (normalized.includes('erro') || normalized.includes('falha')) return 'erro';
    if (normalized.includes('pendente') || normalized.includes('recebendo')) return 'pendente';
    if (normalized.includes('conclu')) return 'concluida';
    
    return normalized;
}

function getPriorityClass(priority) {
    switch(priority) {
        case 1: return 'baixa';
        case 2: return 'normal';
        case 3: return 'alta';
        default: return 'normal';
    }
}

function getPriorityLabel(priority) {
    switch(priority) {
        case 1: return 'Baixa';
        case 2: return 'Normal';
        case 3: return 'Alta';
        default: return 'Normal';
    }
}

function calculateDistance(x1, y1, x2, y2) {
    return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
}

function showError(containerId, message) {
    const container = document.getElementById(containerId);
    if (container) {
        container.innerHTML = `<div class="empty-state">${message}</div>`;
    }
}

// ========== Cleanup ==========
window.addEventListener('beforeunload', () => {
    if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval);
    }
});