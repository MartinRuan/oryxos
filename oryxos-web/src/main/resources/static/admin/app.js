const endpoints = {
  sessions: "/api/v1/sessions", agents: "/api/v1/agents", workspace: "/api/v1/workspace",
  schedules: "/api/v2/schedules", tools: "/api/v1/tools", memory: "/api/v1/memory", runtime: "/api/v1/info"
};
const builtinToolNames = new Set([
  "read_file", "write_file", "list_dir", "shell", "http_get", "http_post",
  "save_memory", "recall_memory", "notify"
]);

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined && text !== null) node.textContent = String(text);
  return node;
}
function card(title, rows, badge) {
  const node = element("article", "card");
  node.append(element("h3", "", title || "未命名"));
  if (badge) node.append(element("span", "badge", badge));
  rows.forEach((row) => node.append(element("p", "", row)));
  return node;
}
async function request(path, options = {}) {
  const response = await fetch(path, { ...options, headers: { Accept: "application/json", ...(options.headers || {}) } });
  const envelope = await response.json().catch(() => ({ message: `HTTP ${response.status}` }));
  if (!response.ok || envelope.code !== 0) throw new Error(envelope.message || "请求失败");
  return envelope.data;
}
function showMessage(container, message, isError) {
  container.replaceChildren(element("div", isError ? "error" : "empty", message));
  container.classList.remove("loading");
}
function renderCollection(id, values, createCard) {
  const container = document.getElementById(`${id}-content`);
  const labels = { sessions: "会话", agents: "Agent", schedules: "定时任务", tools: "工具" };
  if (!Array.isArray(values) || values.length === 0) return showMessage(container, `暂无${labels[id] || "数据"}`, false);
  container.replaceChildren(...values.map(createCard));
  container.classList.remove("loading");
}
function setDashboardMetric(id, value, detail) {
  document.getElementById(`${id}-value`).textContent = value;
  document.getElementById(`${id}-detail`).textContent = detail;
}
function formatTime(value) { return value ? new Date(value).toLocaleString("zh-CN") : "从未"; }
function translateStatus(value) {
  const labels = {
    active: "活跃", archived: "已归档", running: "运行中", stopped: "已停止",
    success: "成功", failed: "失败", pending: "等待中", completed: "已完成"
  };
  return labels[String(value || "").toLowerCase()] || value || "未知";
}
function translateProviderType(value) {
  const labels = { cloud: "云端", local: "本地", mock: "模拟" };
  return labels[String(value || "").toLowerCase()] || value || "未知类型";
}

function renderSessionMessages(container, detail) {
  const messages = Array.isArray(detail.messages) ? detail.messages : [];
  container.replaceChildren(element("p", "session-meta", `${detail.channel || "未知渠道"} · ${detail.messageCount || 0} 条消息 · ${translateStatus(detail.status)}`));
  messages.forEach((message) => {
    const node = element("div", "message");
    node.append(element("span", "message-role", ({ user: "用户", assistant: "助手", system: "系统", tool: "工具" }[message.role] || message.role || "未知")), element("pre", "message-content", message.content || ""));
    container.append(node);
  });
  if (messages.length === 0) container.append(element("p", "empty-inline", "该会话暂无消息"));
}
function sessionCard(item) {
  const node = card(item.id, [`Agent · ${item.profileName}`, `用户 · ${item.userId}`, `${item.messageCount} 条消息`], translateStatus(item.status));
  node.classList.add("session-card");
  const toggle = element("button", "session-toggle", "查看消息");
  const details = element("div", "session-details"); details.hidden = true;
  toggle.addEventListener("click", async () => {
    details.hidden = !details.hidden;
    toggle.textContent = details.hidden ? "查看消息" : "收起消息";
    if (!details.hidden && !details.dataset.loaded) {
      try { renderSessionMessages(details, await request(`${endpoints.sessions}/${encodeURIComponent(item.id)}`)); details.dataset.loaded = "true"; }
      catch (error) { details.replaceChildren(element("p", "session-error", error.message)); }
    }
  });
  node.append(toggle, details); return node;
}
function agentDetail(label, value) {
  const node = element("div", "agent-detail-item");
  const displayValue = value === undefined || value === null || value === "" ? "无" : value;
  node.append(element("span", "", label), element("strong", "", displayValue));
  return node;
}
function selectAgentTab(panelId) {
  document.querySelectorAll(".agent-tab-panel").forEach((panel) => {
    const selected = panel.id === panelId;
    panel.hidden = !selected;
    panel.classList.toggle("active", selected);
  });
  document.querySelectorAll("[data-agent-tab]").forEach((tab) => {
    const selected = tab.dataset.agentTab === panelId;
    tab.classList.toggle("active", selected);
    tab.setAttribute("aria-selected", String(selected));
  });
}
function showAgentList() {
  document.getElementById("agent-detail-view").hidden = true;
  document.getElementById("agent-list-view").hidden = false;
  document.getElementById("page-title").textContent = "Agent列表";
}
function findWorkspaceNode(nodes, path) {
  for (const node of nodes || []) {
    if (node.path === path) return node;
    const found = findWorkspaceNode(node.children, path);
    if (found) return found;
  }
  return null;
}
function renderAgentFiles(agentName, tree, item) {
  const container = document.getElementById("agent-files-content");
  const directory = findWorkspaceNode(tree, `agents/${agentName}`);
  const children = directory ? directory.children || [] : [];
  if (children.length === 0) {
    showMessage(container, "该 Agent 暂无可查看文件", false);
  } else {
    container.replaceChildren(...children.map((child) => fileNode(child, "agent-file-preview")));
    container.classList.remove("loading");
  }
  document.getElementById("agent-file-preview").textContent =
      item.agentMarkdown || "请选择文件";
}
function renderAgentSessions(agentName, sessions) {
  const associated = (sessions || []).filter((session) => session.profileName === agentName);
  document.getElementById("agent-session-count").textContent = `(${associated.length})`;
  const container = document.getElementById("agent-sessions-content");
  if (associated.length === 0) {
    showMessage(container, "该 Agent 暂无关联会话", false);
  } else {
    container.replaceChildren(...associated.map(sessionCard));
    container.classList.remove("loading");
  }
}
function renderAgentMemory(data) {
  const container = document.getElementById("agent-memory-content");
  const content = data && data.content ? data.content : "";
  const hasEntries = content.split("\n").some((line) => {
    const value = line.trim();
    return value && !value.startsWith("## ");
  });
  container.textContent = hasEntries ? content : "该 Agent 暂无关联记忆";
  container.classList.remove("loading");
}
async function showAgentDetails(item) {
  const provider = item.provider || {};
  document.getElementById("agent-list-view").hidden = true;
  document.getElementById("agent-detail-view").hidden = false;
  document.getElementById("agent-detail-title").textContent = item.name;
  document.getElementById("page-title").textContent = `${item.name} · Agent详情`;
  selectAgentTab("agent-basic-panel");

  const details = element("div", "agent-detail-grid");
  details.append(
    agentDetail("描述", item.description || "暂无描述"),
    agentDetail("Provider", provider.name),
    agentDetail("模型", provider.model),
    agentDetail("温度", provider.temperature),
    agentDetail("工具", (item.tools || []).join(", ")),
    agentDetail("MCP Server", (item.mcpServers || []).join(", ")),
    agentDetail("定时任务", `${(item.schedules || []).length} 个`),
    agentDetail("来源", item.sourcePath)
  );
  const instructions = element("section", "agent-instructions");
  instructions.append(
    element("h3", "", "任务指令"),
    element("pre", "memory-block", item.instructions || "暂无任务指令")
  );
  document.getElementById("agent-basic-panel").replaceChildren(details, instructions);
  document.getElementById("agent-files-content").textContent = "正在加载文件…";
  document.getElementById("agent-files-content").classList.add("loading");
  document.getElementById("agent-sessions-content").textContent = "正在加载会话…";
  document.getElementById("agent-sessions-content").classList.add("loading");
  document.getElementById("agent-session-count").textContent = "";
  document.getElementById("agent-memory-content").textContent = "正在加载记忆…";
  document.getElementById("agent-memory-content").classList.add("loading");

  const [treeResult, sessionsResult, memoryResult] = await Promise.allSettled([
    request(`${endpoints.workspace}/tree`),
    request(endpoints.sessions),
    request(`${endpoints.memory}?agent=${encodeURIComponent(item.name)}`)
  ]);
  if (treeResult.status === "fulfilled") {
    renderAgentFiles(item.name, treeResult.value, item);
  } else {
    showMessage(document.getElementById("agent-files-content"), treeResult.reason.message, true);
  }
  if (sessionsResult.status === "fulfilled") {
    renderAgentSessions(item.name, sessionsResult.value);
  } else {
    showMessage(document.getElementById("agent-sessions-content"), sessionsResult.reason.message, true);
  }
  if (memoryResult.status === "fulfilled") {
    renderAgentMemory(memoryResult.value);
  } else {
    showMessage(document.getElementById("agent-memory-content"), memoryResult.reason.message, true);
  }
}
function openAgentForm(item) {
  const form = document.getElementById("agent-form");
  form.reset(); delete form.dataset.editing;
  document.getElementById("agent-name").disabled = false;
  setAgentFeedback("");
  if (item) {
    form.dataset.editing = item.name;
    document.getElementById("agent-name").value = item.name;
    document.getElementById("agent-name").disabled = true;
    document.getElementById("agent-markdown").value = item.agentMarkdown || "";
  }
  form.hidden = false;
  document.getElementById(item ? "agent-markdown" : "generation-sentence").focus();
}
function agentRow(item) {
  const provider = item.provider || {};
  const row = element("tr");
  [
    item.name,
    item.description || "暂无描述",
    provider.name || "无",
    provider.model || "无",
    `${(item.tools || []).length} 个`
  ].forEach((value) => row.append(element("td", "", value)));
  const actionsCell = element("td");
  const actions = element("div", "agent-actions");
  const detail = element("button", "agent-action", "详情");
  const edit = element("button", "agent-action", "编辑");
  const remove = element("button", "agent-action danger", "删除");
  [detail, edit, remove].forEach((button) => { button.type = "button"; });
  detail.addEventListener("click", () => showAgentDetails(item));
  edit.addEventListener("click", () => openAgentForm(item));
  remove.addEventListener("click", async () => {
    if (!window.confirm(`确定删除 Agent ${item.name}？Agent 目录将移入归档区。`)) return;
    try {
      await request(`${endpoints.agents}/${encodeURIComponent(item.name)}`, { method: "DELETE" });
      showAgentList();
      await Promise.all([loadAgents(), loadWorkspace(), loadSchedules()]);
    } catch (error) { window.alert(`删除失败：${error.message}`); }
  });
  actions.append(detail, edit, remove);
  actionsCell.append(actions); row.append(actionsCell);
  return row;
}
function showAgentMessage(message, isError = false) {
  const row = element("tr");
  const cell = element("td", isError ? "table-message error-text" : "table-message", message);
  cell.colSpan = 6; row.append(cell);
  document.getElementById("agents-content").replaceChildren(row);
}
function renderAgentList(agents) {
  if (!Array.isArray(agents) || agents.length === 0) return showAgentMessage("暂无 Agent");
  document.getElementById("agents-content").replaceChildren(...agents.map(agentRow));
}
function setAgentFeedback(message, error = false) {
  const node = document.getElementById("agent-feedback"); node.textContent = message; node.className = error ? "session-error" : "";
}
function resetAgentForm() {
  const form = document.getElementById("agent-form"); form.reset(); delete form.dataset.editing;
  document.getElementById("agent-name").disabled = false; setAgentFeedback(""); form.hidden = true;
}
async function loadSessions() {
  const sessions = await request(endpoints.sessions);
  renderCollection("sessions", sessions, sessionCard);
  const activeCount = sessions.filter((item) => String(item.status).toLowerCase() === "active").length;
  setDashboardMetric("session-count", activeCount, `共 ${sessions.length} 个会话`);
}
async function loadAgents() {
  const agents = await request(endpoints.agents);
  renderAgentList(agents);
  setDashboardMetric("agent-count", agents.length, "当前已注册 Agent");
}
async function loadTools() {
  const tools = await request(endpoints.tools);
  renderCollection("tools", tools, (item) => card(item.name, [item.description || "暂无描述", `输入结构 · ${item.inputSchema || "{}"}`]));
  const builtinCount = tools.filter((item) => builtinToolNames.has(item.name)).length;
  setDashboardMetric("tool-count", builtinCount, `共注册 ${tools.length} 个工具`);
}

function fileNode(node, previewId = "file-preview") {
  if (node.type === "FILE") {
    const button = element("button", "tree-file", node.name); button.type = "button";
    button.addEventListener("click", async () => {
      const preview = document.getElementById(previewId); preview.textContent = "正在加载…";
      try { preview.textContent = await request(`${endpoints.workspace}/file?path=${encodeURIComponent(node.path)}`); }
      catch (error) { preview.textContent = error.message; }
    }); return button;
  }
  const details = element("details", "tree-directory"); details.open = node.path === "agents";
  details.append(element("summary", "", node.name));
  (node.children || []).forEach((child) => details.append(fileNode(child, previewId)));
  return details;
}
async function loadWorkspace() {
  const container = document.getElementById("workspace-content");
  const tree = await request(`${endpoints.workspace}/tree`);
  container.replaceChildren(...tree.map((node) => fileNode(node))); container.classList.remove("loading");
}
function scheduleCard(item) {
  const node = card(item.displayName || item.scheduleKey, [`Agent · ${item.profileName}`, `Cron · ${item.cron}`, `时区 · ${item.zone}`, `下次运行 · ${item.enabled ? formatTime(item.nextRunAt) : "已停止"}`, `上次结果 · ${item.lastStatus ? translateStatus(item.lastStatus) : "从未运行"}`], item.enabled ? "运行中" : "已停止");
  const actions = element("div", "schedule-actions");
  const toggle = element("button", "schedule-control", item.enabled ? "停止" : "启动");
  const run = element("button", "schedule-control secondary", "立即运行");
  toggle.addEventListener("click", async () => { await request(`${endpoints.schedules}/${encodeURIComponent(item.scheduleId)}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ enabled: !item.enabled }) }); await loadSchedules(); });
  run.addEventListener("click", async () => { await request(`${endpoints.schedules}/${encodeURIComponent(item.scheduleId)}/run`, { method: "POST" }); await loadSchedules(); });
  actions.append(toggle, run); node.append(actions); return node;
}
async function loadSchedules() { renderCollection("schedules", await request(endpoints.schedules), scheduleCard); }
async function loadRuntime() {
  const data = await request(endpoints.runtime);
  const providerContainer = document.getElementById("provider-content");
  const providers = Array.isArray(data.providers) ? data.providers : [];
  if (providers.length === 0) {
    showMessage(providerContainer, "暂无模型提供商", false);
  } else {
    providerContainer.replaceChildren(...providers.map((item) => card(item.name, [`${translateProviderType(item.type)} · ${item.defaultModel}`], item.available ? "可用" : "不可用")));
    providerContainer.classList.remove("loading");
  }
  setDashboardMetric("provider-count", providers.length, `${providers.filter((item) => item.available).length} 个可用`);
  setDashboardMetric("runtime-status", "运行正常", `${data.name} · ${data.version}`);
}

async function loadAll() {
  const jobs = [
    loadSessions(),
    loadAgents(), loadWorkspace(), loadSchedules(),
    loadTools(), loadRuntime()
  ];
  const ids = ["sessions", "agents", "workspace", "schedules", "tools", "runtime"];
  (await Promise.allSettled(jobs)).forEach((result, index) => {
    if (result.status !== "rejected") return;
    const id = ids[index];
    if (id === "agents") {
      showAgentMessage(result.reason.message, true);
    } else if (id === "runtime") {
      showMessage(document.getElementById("provider-content"), result.reason.message, true);
      setDashboardMetric("runtime-status", "连接异常", result.reason.message);
    } else {
      showMessage(document.getElementById(`${id}-content`), result.reason.message, true);
    }
  });
  document.getElementById("last-updated").textContent = `更新于 ${new Date().toLocaleTimeString("zh-CN")}`;
}

document.getElementById("generate-agent").addEventListener("click", async () => {
  const sentence = document.getElementById("generation-sentence").value;
  try { setAgentFeedback("正在生成…"); document.getElementById("agent-markdown").value = await request(`${endpoints.agents}/generate`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ sentence }) }); setAgentFeedback("草稿已生成，请确认内容后再保存。"); }
  catch (error) { setAgentFeedback(error.message, true); }
});
document.getElementById("agent-form").addEventListener("submit", async (event) => {
  event.preventDefault(); const form = event.currentTarget; const editing = form.dataset.editing;
  const name = editing || document.getElementById("agent-name").value;
  const body = JSON.stringify({ name, agentMarkdown: document.getElementById("agent-markdown").value });
  try { await request(editing ? `${endpoints.agents}/${encodeURIComponent(editing)}` : endpoints.agents, { method: editing ? "PUT" : "POST", headers: { "Content-Type": "application/json" }, body }); resetAgentForm(); await Promise.all([loadAgents(), loadWorkspace(), loadSchedules()]); }
  catch (error) { setAgentFeedback(error.message, true); }
});
document.getElementById("new-agent").addEventListener("click", () => openAgentForm());
document.getElementById("back-agent-list").addEventListener("click", showAgentList);
document.querySelectorAll("[data-agent-tab]").forEach((tab) => {
  tab.addEventListener("click", () => selectAgentTab(tab.dataset.agentTab));
});
document.getElementById("reset-agent").addEventListener("click", resetAgentForm);
function selectRuntimeView(sectionId) {
  document.querySelectorAll(".runtime-view").forEach((node) => node.classList.remove("active"));
  document.getElementById(sectionId).classList.add("active");
}
const runtimeGroup = document.querySelector(".nav-group");
const runtimeToggle = document.querySelector(".nav-group > a");
document.querySelectorAll("nav a").forEach((link) => link.addEventListener("click", (event) => {
  event.preventDefault();
  const isRuntimeToggle = link === runtimeToggle;
  const runtimeWasActive = document.getElementById("runtime").classList.contains("active");
  if (isRuntimeToggle && runtimeWasActive) {
    const collapsed = runtimeGroup.classList.toggle("collapsed");
    runtimeToggle.setAttribute("aria-expanded", String(!collapsed));
    return;
  }
  if (isRuntimeToggle || link.dataset.section) {
    runtimeGroup.classList.remove("collapsed");
    runtimeToggle.setAttribute("aria-expanded", "true");
  }

  document.querySelectorAll("nav a, .panel").forEach((node) => node.classList.remove("active"));
  document.getElementById(link.dataset.target).classList.add("active");
  if (link.dataset.section) {
    runtimeToggle.classList.add("active");
    link.classList.add("active");
    selectRuntimeView(link.dataset.section);
    document.getElementById("page-title").textContent = "OS运行时";
  } else {
    link.classList.add("active");
    document.getElementById("page-title").textContent = link.textContent;
    if (link.dataset.target === "agents") showAgentList();
    if (isRuntimeToggle) {
      selectRuntimeView("runtime-providers");
      document.querySelector('[data-section="runtime-providers"]').classList.add("active");
    }
  }
  window.scrollTo({ top: 0, behavior: "smooth" });
}));
loadAll();
