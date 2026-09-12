const endpoints = {
  sessions: "/api/v1/sessions",
  profiles: "/api/v1/profiles",
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

function showMessage(container, message, isError) {
  container.replaceChildren(element("div", isError ? "error" : "empty", message));
  container.classList.remove("loading");
}

async function request(path) {
  const response = await fetch(path, { headers: { Accept: "application/json" } });
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

async function loadAll() {
  const jobs = [
    request(endpoints.sessions).then((data) => renderCollection("sessions", data, (item) =>
      card(item.id, [`Profile · ${item.profileName}`, `User · ${item.userId}`, `${item.messageCount} messages`], item.status))),
    request(endpoints.profiles).then((data) => renderCollection("profiles", data, (item) =>
      card(item.name, [item.description || "No description", `${item.provider || "No provider"} · ${item.model || "No model"}`, `Tools · ${(item.tools || []).join(", ") || "None"}`]))),
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

  const ids = ["sessions", "profiles", "tools", "memory", "runtime"];
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
