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
    console.log('[Ground Control] Initializing...');
    
    checkConnection();
    loadAllData();
    startAutoRefresh();
    setupEventListeners();
    setupFilters();
    
    addActivity('info', 'Sistema Ground Control iniciado');
    console.log('[Ground Control] Ready');
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
        const response = await fetch(`${API_BASE}/rovers`);
        updateConnectionStatus(response.ok);
    } catch (error) {
        console.error('[API] Connection failed:', error);
        updateConnectionStatus(false);
        addActivity('error', 'Falha na conexão com a API');
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
        console.log('[API] Fetching rovers...');
        const response = await fetch(`${API_BASE}/rovers`);
        const rovers = await response.json();
        
        console.log('[API] Rovers loaded:', rovers.length);
        
        // Detect changes
        detectRoverChanges(rovers);
        
        previousData.rovers = rovers;
        renderRovers(rovers);
        updateConnectionStatus(true);
    } catch (error) {
        console.error('[API] Error loading rovers:', error);
        showError('rovers-container', 'Erro ao carregar rovers');
        updateConnectionStatus(false);
    }
}

async function refreshMissions() {
    try {
        console.log('[API] Fetching missions...');
        const response = await fetch(`${API_BASE}/missoes`);
        const missions = await response.json();
        
        console.log('[API] Missions loaded:', missions.length);
        
        // Detect changes
        detectMissionChanges(missions);
        
        previousData.missions = missions;
        renderMissions(missions);
        updateConnectionStatus(true);
    } catch (error) {
        console.error('[API] Error loading missions:', error);
        showError('missions-container', 'Erro ao carregar missões');
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
    
    if (!rovers || rovers.length === 0) {
        container.innerHTML = '<div class="empty-state">Nenhum rover registrado</div>';
        return;
    }
    
    container.innerHTML = rovers.map(rover => createRoverCard(rover)).join('');
    filterRovers();
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
    
    if (!missions || missions.length === 0) {
        container.innerHTML = '<div class="empty-state">Nenhuma missão registrada</div>';
        return;
    }
    
    container.innerHTML = missions.map(mission => createMissionCard(mission)).join('');
    filterMissions();
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
    const normalized = estado.toLowerCase().replace(/_/g, '-').replace(/\s+/g, '-');
    
    if (normalized.includes('disponivel')) return 'disponivel';
    if (normalized.includes('missao') || normalized.includes('execucao')) return 'em-missao';
    if (normalized.includes('erro') || normalized.includes('falha')) return 'erro';
    if (normalized.includes('pendente')) return 'pendente';
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