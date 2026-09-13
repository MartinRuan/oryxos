const endpoints = {
  sessions: "/api/v1/sessions",
  profiles: "/api/v1/profiles",
  schedules: "/api/v2/schedules",
  tools: "/api/v1/tools",
  memory: "/api/v1/memory",
  runtime: "/api/v1/info"
};

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined && text !== null) node.textContent = String(text);
  return node;
}

function card(title, rows, badge) {
  const node = element("article", "card");
  const heading = element("h3", "", title || "Unnamed");
  node.append(heading);
  if (badge) node.append(element("span", "badge", badge));
  rows.forEach((row) => node.append(element("p", "", row)));
  return node;
}

function renderSessionMessages(container, detail) {
  const messages = Array.isArray(detail.messages) ? detail.messages : [];
  const summary = element(
    "p",
    "session-meta",
    `${detail.channel || "unknown"} · ${detail.messageCount || 0} messages · ${detail.status || "unknown"}`
  );
  container.replaceChildren(summary);
  if (messages.length === 0) {
    container.append(element("p", "empty-inline", "No messages in this session"));
    return;
  }
  messages.forEach((message) => {
    const messageNode = element("div", "message");
    const role = element("span", "message-role", message.role || "unknown");
    const content = element("pre", "message-content", message.content || "");
    messageNode.append(role, content);
    if (message.toolCallId) {
      messageNode.append(element("span", "message-tool-call", `Tool call · ${message.toolCallId}`));
    }
    container.append(messageNode);
  });
}

function sessionCard(item) {
  const node = card(
    item.id,
    [`Agent · ${item.profileName}`, `User · ${item.userId}`, `${item.messageCount} messages`],
    item.status
  );
  node.classList.add("session-card");

  const toggle = element("button", "session-toggle", "View messages");
  toggle.type = "button";
  toggle.setAttribute("aria-expanded", "false");
  const details = element("div", "session-details");
  details.hidden = true;
  let loaded = false;

  toggle.addEventListener("click", async () => {
    if (!details.hidden) {
      details.hidden = true;
      toggle.textContent = "View messages";
      toggle.setAttribute("aria-expanded", "false");
      return;
    }

    details.hidden = false;
    toggle.textContent = "Hide messages";
    toggle.setAttribute("aria-expanded", "true");
    if (loaded) return;

    toggle.disabled = true;
    details.replaceChildren(element("p", "loading-inline", "Loading messages…"));
    try {
      const detail = await request(`${endpoints.sessions}/${encodeURIComponent(item.id)}`);
      renderSessionMessages(details, detail);
      loaded = true;
    } catch (error) {
      details.replaceChildren(element("p", "session-error", error.message));
      toggle.textContent = "Retry messages";
    } finally {
      toggle.disabled = false;
    }
  });

  node.append(toggle, details);
  return node;
}

function agentCard(item) {
  const rows = [
    item.description || "No description",
    `Provider · ${item.provider || "None"}`,
    `Model · ${item.model || "None"}`,
    `Tools · ${(item.tools || []).join(", ") || "None"}`
  ];
  return card(item.name, rows);
}

function showMessage(container, message, isError) {
  container.replaceChildren(element("div", isError ? "error" : "empty", message));
  container.classList.remove("loading");
}

async function request(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { Accept: "application/json", ...(options.headers || {}) }
  });
  const envelope = await response.json().catch(() => ({ message: `HTTP ${response.status}` }));
  if (!response.ok || envelope.code !== 0) throw new Error(envelope.message || "Request failed");
  return envelope.data;
}

function renderCollection(id, values, createCard) {
  const container = document.getElementById(`${id}-content`);
  if (!Array.isArray(values) || values.length === 0) {
    showMessage(container, `No ${id} found`, false);
    return;
  }
  container.replaceChildren(...values.map(createCard));
  container.classList.remove("loading");
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : "Never";
}

function scheduleCard(item) {
  const node = card(
    item.displayName || item.scheduleKey,
    [
      `Agent · ${item.profileName}`,
      `Cron · ${item.cron}`,
      `Timezone · ${item.zone}`,
      `Next run · ${item.enabled ? formatTime(item.nextRunAt) : "Stopped"}`,
      `Last run · ${formatTime(item.lastRunAt)}`,
      `Last result · ${item.lastStatus || "Never"} · ${item.runCount} runs`
    ],
    item.enabled ? "running" : "stopped"
  );
  node.classList.add("schedule-card");

  const actions = element("div", "schedule-actions");
  const toggle = element("button", "schedule-control", item.enabled ? "Stop" : "Start");
  const run = element("button", "schedule-control secondary", "Run now");
  const feedback = element("p", "schedule-feedback");
  toggle.type = "button";
  run.type = "button";
  actions.append(toggle, run);
  node.append(actions, feedback);

  toggle.addEventListener("click", async () => {
    toggle.disabled = true;
    run.disabled = true;
    feedback.textContent = item.enabled ? "Stopping…" : "Starting…";
    try {
      await request(`${endpoints.schedules}/${encodeURIComponent(item.scheduleId)}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ enabled: !item.enabled })
      });
      await loadSchedules();
    } catch (error) {
      feedback.textContent = error.message;
      feedback.classList.add("session-error");
      toggle.disabled = false;
      run.disabled = false;
    }
  });

  run.addEventListener("click", async () => {
    toggle.disabled = true;
    run.disabled = true;
    feedback.textContent = "Running…";
    try {
      await request(`${endpoints.schedules}/${encodeURIComponent(item.scheduleId)}/run`, {
        method: "POST"
      });
      await loadSchedules();
    } catch (error) {
      feedback.textContent = error.message;
      feedback.classList.add("session-error");
      toggle.disabled = false;
      run.disabled = false;
    }
  });
  return node;
}

async function loadSchedules() {
  const data = await request(endpoints.schedules);
  renderCollection("schedules", data, scheduleCard);
}

async function loadAll() {
  const jobs = [
    request(endpoints.sessions).then((data) => renderCollection("sessions", data, (item) =>
      sessionCard(item))),
    request(endpoints.profiles).then((data) => renderCollection("profiles", data, (item) =>
      agentCard(item))),
    loadSchedules(),
    request(endpoints.tools).then((data) => renderCollection("tools", data, (item) =>
      card(item.name, [item.description || "No description", item.inputSchema || "{}"]))),
    request(endpoints.memory).then((data) => {
      const container = document.getElementById("memory-content");
      container.textContent = data.content || "No long-term memory stored";
      container.classList.remove("loading");
    }),
    request(endpoints.runtime).then((data) => {
      const providers = data.providers || [];
      const base = card(data.name, [`Version · ${data.version}`, `Java · ${data.javaVersion}`], "UP");
      const providerCards = providers.map((item) => card(item.name,
        [`${item.type} · ${item.defaultModel}`, `Models · ${(item.supportedModels || []).join(", ")}`],
        item.available ? "available" : "unavailable"));
      const container = document.getElementById("runtime-content");
      container.replaceChildren(base, ...providerCards);
      container.classList.remove("loading");
    })
  ];

  const ids = ["sessions", "profiles", "schedules", "tools", "memory", "runtime"];
  const results = await Promise.allSettled(jobs);
  results.forEach((result, index) => {
    if (result.status === "rejected") {
      showMessage(document.getElementById(`${ids[index]}-content`), result.reason.message, true);
    }
  });
  document.getElementById("last-updated").textContent = `Updated ${new Date().toLocaleTimeString()}`;
}

document.querySelectorAll("nav a").forEach((link) => {
  link.addEventListener("click", () => {
    document.querySelectorAll("nav a, .panel").forEach((node) => node.classList.remove("active"));
    link.classList.add("active");
    document.getElementById(link.dataset.target).classList.add("active");
    document.getElementById("page-title").textContent = link.textContent;
  });
});

loadAll();
